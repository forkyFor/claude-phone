#!/usr/bin/env python3
"""Checks the phone voice commands on an emulator (English locale), after ui-walk.py.

CI emulators have no microphone, so each sentence is handed to the app as if the speech
recogniser had returned it (debug builds only, extra "probe_say"). Checks:
  1. "voice commands"      -> the help dialog opens
  2. "what time is it"     -> the spoken/banner answer appears
  3. "call 123 4567" + Cancel -> confirmation dialog with countdown, no call placed
  4. "call 123 4567", wait -> after the countdown a real call starts (emulator modem), then hung up
  5. typing "what time is it" in the Type box -> the same answer
  6. "open settings"       -> Android settings come to the front
Exit code 0 = all passed.
"""
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.forkyfor.oldphone"
results = []


def adb(*args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout).stdout


def dump():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        try:
            return ET.fromstring(xml[xml.index("<?xml"):])
        except Exception:
            time.sleep(2)
    return ET.fromstring("<hierarchy/>")


def texts(root):
    return [n.get("text") or n.get("content-desc") or "" for n in root.iter("node")]


def node(root, label):
    for n in root.iter("node"):
        if (n.get("text") or "").upper() == label.upper():
            return n
    return None


def tap(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)


def say(sentence, wait=5):
    # adb joins the arguments with spaces for the device shell: the sentence must be quoted.
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--ez", "probe", "true", "--es", "probe_say", shlex.quote(sentence))
    time.sleep(wait)
    return dump()


def check(name, ok, detail="", root=None):
    results.append(ok)
    print(f"VOICE {'OK  ' if ok else 'FAIL'} {name} {detail}".rstrip(), flush=True)
    if not ok and root is not None:
        print("      on screen: " + " | ".join(t for t in texts(root) if t)[:400], flush=True)


def top_package():
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"(?:mResumedActivity|topResumedActivity|mFocusedActivity)[^\n]*? ([\w.]+)/", out)
    return m.group(1) if m else ""


def main():
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(6)

    root = say("voice commands")
    check("help dialog", any("Voice commands" in t for t in texts(root)), root=root)
    ok = node(root, "OK")
    if ok is not None:
        tap(ok)

    root = say("what time is it")
    check("time answer", any(t.startswith("It is ") for t in texts(root)), "| ".join(t for t in texts(root) if t.startswith("It is")), root=root)

    root = say("call 123 4567", wait=3)
    # Dialog buttons are often shown in capitals ("CALL (4)"): compare without case.
    shown = [t for t in texts(root) if t.lower().startswith("call") or t == "Phone call"]
    check("call confirmation with countdown", any(re.match(r"Call \(\d\)", t, re.I) for t in shown), " | ".join(shown), root=root)
    cancel = node(root, "Cancel")
    if cancel is not None:
        tap(cancel)
    time.sleep(6)
    check("cancel places no call", "mState=ACTIVE" not in adb("shell", "dumpsys", "telecom") and "dialer" not in top_package().lower(),
          top_package())

    say("call 123 4567", wait=12)
    telecom = adb("shell", "dumpsys", "telecom")
    registry = adb("shell", "dumpsys", "telephony.registry")
    top = top_package().lower()
    calling = ("mCallState=2" in registry or "1234567" in telecom or "incall" in top or "dialer" in top)
    check("countdown places the call", calling, top_package())
    adb("shell", "input", "keyevent", "KEYCODE_ENDCALL")
    time.sleep(3)

    # Typed command: the "Type" button on the bar, same commands as the microphone.
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    root = dump()
    typed = node(root, "Type")
    if typed is not None:
        tap(typed)
        adb("shell", "input", "text", "what%stime%sis%sit")
        time.sleep(1)
        send = node(dump(), "Send")
        if send is not None:
            tap(send)
        time.sleep(3)
        root = dump()
    check("typed command", typed is not None and any(t.startswith("It is ") for t in texts(root)), root=root)

    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    say("open settings", wait=6)
    pkg = top_package()
    check("open settings", "settings" in pkg.lower(), pkg)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")

    print("VOICE RESULT", "OK" if all(results) else "FAILED", flush=True)
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
