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


def probe_start(*extras):
    args = ["shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--ez", "probe", "true"]
    for e in extras:
        args += list(e)
    adb(*args)


def meeting_files():
    # One name per line: note names contain spaces.
    return [l.strip() for l in adb("shell", "run-as", PKG, "ls", "-1", "files/meetings").splitlines() if l.strip()]


def meetings():
    """Meetings (Android 10+): record, save, NotebookLM's answer simulated, audio deleted."""
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip() or 0)
    if api < 29:
        print("VOICE skip meetings (needs Android 10)", flush=True)
        return
    probe_start(("--ez", "probe_meetings", "true"))
    time.sleep(3)
    root = say("open meetings")
    check("meetings screen", any("Record a meeting" in t for t in texts(root)), root=root)
    rec = node(root, "● Record a meeting")
    if rec is not None:
        tap(rec)
        time.sleep(6)
        root = dump()
        stop = node(root, "■ Stop recording")
        failed = any("could not start" in t for t in texts(root))
        check("recording starts or explains why not", stop is not None or failed, root=root)
        if stop is not None:
            tap(stop)
            time.sleep(3)
            root = dump()
            check("asks to save or discard", node(root, "Save") is not None, root=root)
            save = node(root, "Save")
            if save is not None:
                tap(save)
                later = node(dump(), "Later")
                if later is not None:
                    tap(later)
        else:
            ok = node(root, "OK")
            if ok is not None:
                tap(ok)
    # NotebookLM is not on emulators: a stored recording and its answer are simulated.
    existing = set(meeting_files())
    probe_start(("--ez", "probe_fake_meeting", "true"))
    time.sleep(4)
    fake = [f for f in meeting_files() if f.endswith(".m4a") and f not in existing]
    check("recording kept until the result", len(fake) == 1, " ".join(fake))
    answer = "TITLE: Budget review\nDESCRIPTION: Quarterly numbers.\nSUMMARY: Costs are down 4%."
    probe_start(("--es", "probe_result", shlex.quote(answer)))
    time.sleep(4)
    root = dump()
    check("suggested title and description", any("Budget review" in t for t in texts(root)), root=root)
    accept = node(root, "Accept")
    if accept is not None:
        tap(accept)
        time.sleep(2)
        later = node(dump(), "Later")
        if later is not None:
            tap(later)
    after = meeting_files()
    check("audio deleted after the result",
          accept is not None and bool(fake) and fake[0] not in after and any(f.endswith("Budget review.md") for f in after),
          " ".join(after))


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
    # Emulators with an old stock WebView show the native "engine too old" screen over the bar.
    anyway = node(root, "Try anyway")
    if anyway is not None:
        tap(anyway)
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

    meetings()
    print("VOICE RESULT", "OK" if all(results) else "FAILED", flush=True)
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
