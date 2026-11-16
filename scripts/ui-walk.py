#!/usr/bin/env python3
"""Walks the first-run guide on an emulator with real taps and prints what is on screen.

Run by scripts/probe.sh after a fresh install of an English-locale emulator. It checks that
the native screens appear in order, that the owner PIN can be set, that the main screen shows
the native bar, and that the hidden gesture + PIN opens the owner menu. It never signs in.
Exit code 0 = every expected screen was reached.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.forkyfor.oldphone"
PIN = "4321"


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


def nodes(root):
    return [n for n in root.iter("node")]


def label(n):
    return n.get("text") or n.get("content-desc") or ""


def texts(root):
    return [label(n) for n in nodes(root) if label(n)]


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(root, *wanted):
    for w in wanted:
        for n in nodes(root):
            if label(n) == w:
                return n
    return None


def tap(n):
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2.5)


def show(step, root):
    print(f"UI_WALK {step}: " + " | ".join(t.replace("\n", " ")[:70] for t in texts(root)[:14]), flush=True)


def press(step, *wanted, optional=False):
    """Taps the first button whose label matches; scrolls once if it is below the fold."""
    for attempt in range(2):
        root = dump()
        if attempt == 0:
            show(step, root)
        n = find(root, *wanted)
        if n is not None:
            tap(n)
            return True
        adb("shell", "input", "swipe", "500", "1400", "500", "500", "300")
        time.sleep(1.5)
    if not optional:
        print(f"UI_WALK FAIL at {step}: none of {wanted} on screen", flush=True)
        sys.exit(1)
    return False


def main():
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip() or 0)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(6)
    for _ in range(4):
        if texts(dump()):
            break
        time.sleep(5)
    else:
        print("UI_WALK UNAVAILABLE: uiautomator returned an empty screen on this image", flush=True)
        sys.exit(2)

    press("welcome", "Start")
    press("language", "English")
    press("wifi", "Continue", "Skip for now")
    press("engine", "Continue", "Continue anyway")
    if api >= 23:
        press("microphone", "Continue", "Not now")
        press("camera", "Continue", "Not now")

    root = dump()
    show("pin", root)
    fields = [n for n in nodes(root) if n.get("class") == "android.widget.EditText"]
    if len(fields) < 2:
        print("UI_WALK FAIL at pin: PIN fields not found", flush=True)
        sys.exit(1)
    for f in fields[:2]:
        tap(f)
        adb("shell", "input", "text", PIN)
        time.sleep(1)
    press("pin-save", "Save")
    press("mode", "Use it as a normal app")
    press("sign-in", "Open Claude")
    time.sleep(25)

    root = dump()
    # Android's own one-time "Viewing full screen" hint.
    hint = find(root, "GOT IT", "Got it")
    if hint is not None:
        tap(hint)
        root = dump()
    show("main", root)
    old_engine = find(root, "Try anyway") is not None
    if old_engine:
        # Stock emulator WebView below the warning threshold: the native explanation is shown.
        print("UI_WALK note: native 'web engine is too old' screen shown", flush=True)
        press("old-engine-owner", "Owner menu")
    else:
        missing = [w for w in ("New chat", "Projects", "Camera", "Speak") if find(root, w) is None]
        if missing:
            print(f"UI_WALK FAIL at main: bar items missing {missing}", flush=True)
            sys.exit(1)
        # Hidden owner gesture: hold the microphone for three seconds.
        x, y = center(find(root, "Speak"))
        adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "3800")
        time.sleep(2.5)
    root = dump()
    show("pin-dialog", root)
    if find(root, "Owner PIN") is None:
        print("UI_WALK FAIL at owner gesture: PIN dialog did not open", flush=True)
        sys.exit(1)
    adb("shell", "input", "text", PIN)
    time.sleep(1)
    press("pin-ok", "OK")
    root = dump()
    show("owner-menu", root)
    if find(root, "Owner menu") is None:
        print("UI_WALK FAIL at owner menu: not shown after the PIN", flush=True)
        sys.exit(1)
    press("owner-back", "Back to Claude")
    root = dump()
    if find(root, "Speak") is None:
        print("UI_WALK FAIL: did not return to the main screen", flush=True)
        sys.exit(1)
    print("UI_WALK OK" + (" (old-engine screen path)" if old_engine else " (bar + hidden gesture path)"), flush=True)


if __name__ == "__main__":
    main()
