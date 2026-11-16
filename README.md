# OldPhoneClaude

**Turn an old Android phone into a dedicated, always-on device for Claude.**

*Italiano: [README.it.md](README.it.md)*

> **Independent project.** OldPhoneClaude is not affiliated with, endorsed by or supported
> by Anthropic. "Claude" is a trademark of Anthropic. This app is a dedicated browser for the
> official website **https://claude.ai**: you sign in there with your own account. The name
> is provisional and may change.

Switch the phone on, talk, use Claude. No address bar, no tabs, no Android home screen: the
phone simply *is* the Claude device on your desk, in the kitchen or next to the bed.

## What it does

- Shows the **official claude.ai site** full screen (chats, projects, everything your
  account has in a browser).
- One discreet native bar with four actions: **New chat**, **Projects**, a big
  **Microphone**, **Camera**. It hides while you type or read.
- Can be the phone's **Home screen** and **assistant app**: the Home button and the
  assistant gesture always lead to Claude; it starts again after a restart.
- **Push-to-talk dictation** with the phone's own speech recognition, three spoken commands
  ("new chat", "open projects", "back"), and optional **read aloud** with a visible Stop.
- **Attach** photos from the camera, pictures and files; **receive** text, links, images and
  PDFs from Android's Share menu into a new chat.
- A **first-run guide** in plain language (English and Italian) and **native screens**
  explaining what is wrong (no network, web engine too old, permission denied) instead of a
  blank page.
- **Always-on behaviour**: screen kept on only while charging, dimming, and a dark rest
  screen with a moving clock for OLED panels.
- Two lock levels: Android **screen pinning** (no computer), or a full **dedicated mode**
  (device owner + lock task) set up once with adb.
- A hidden **owner menu** (hold the microphone 3 s + a PIN you choose): Wi-Fi, volume,
  brightness, web engine, updates, sign out, leave dedicated mode.

## What it does NOT do

- **No Anthropic API and no API key.** It is not an alternative client.
- It does **not read, store or send cookies, tokens or passwords**, calls no private
  endpoints, does not scrape conversations and does not work around blocks, bot checks,
  limits or sign-in restrictions.
- It does **not replace, hide or restyle** the Claude interface. No style tweaks are applied
  to the page at all.
- **No server of the author, no analytics, no ads.** The only network traffic is the site
  you see. (The phone's speech recognition service, if you use the microphone button, is
  whatever your phone has, often Google's.)
- **No root**, no system partitions, no boot animation or lock screen replacement, no
  disabling of Android protections.
- No "Hey Claude" hot word: the phone never listens with the screen off.

## Requirements

- An Android phone with **Android 5.0 or newer** to install the app. Whether the site
  actually works depends on the **web engine**: see [Compatibility](#compatibility).
- **Your own Claude account** (free or paid). No API key.
- Wi-Fi. A charger and a battery in good condition: read
  [Battery and safety](#battery-and-safety).
- No computer, no Mac, no developer account. A computer with `adb` is needed only for the
  optional dedicated mode.

## Install

Step-by-step guide for non-technical people, including "unknown sources" and updates:
**[docs/en/INSTALL.md](docs/en/INSTALL.md)**.

Short version: on the phone, open the
[Releases](https://github.com/forkyFor/claude-phone/releases) page, download the `lite` APK,
allow the installation, open it and follow the guide.

| Edition | Android | Web engine | APK size |
|---|---|---|---|
| **lite** | 5.0+ | the phone's Android System WebView | about 0.1 MB (signed release), about 1 MB (debug) |
| **gecko** | 8.0+ | WebView **or** bundled GeckoView (Firefox's engine), selectable in the owner menu | large: 160–200 MB as a debug build |

## What works with what

| Feature | APK only | Needs adb (once) | Not possible |
|---|:-:|:-:|:-:|
| Official site full screen, persistent sign-in, back button, reload | ✔ | | |
| Native bar, dictation, read aloud, camera, file attach, share-in | ✔ | | |
| Home screen: Home button returns to Claude, starts after reboot | ✔ | | |
| Assistant gesture starts listening | ✔ (you pick the app in Android settings) | | |
| Immersive full screen (bars return with an edge swipe) | ✔ | | |
| Keep screen on while charging, dimming, rest screen | ✔ | | |
| Screen pinning (basic lock) | ✔ (Android asks to confirm) | | |
| Start at boot without being the Home app, Android 10+ | | | ✘ Android forbids it |
| Dedicated mode: no status bar, notifications, Home/Recents, exit only with PIN | | ✔ `dpm set-device-owner` on a reset phone without accounts | |
| Hiding the power menu / preventing power off | | | ✘ not attempted (safety) |
| "Hey Claude" with the screen off | | | ✘ not a goal |
| Sign-in with Google inside the system WebView | | | ✘ usually blocked by Google; not worked around |
| Passing a sign-in done in Chrome/Custom Tabs to the app | | | ✘ would require copying cookies, which this app refuses to do |
| Replacing the boot animation or lock screen, anything needing root | | | ✘ out of scope |

Both lock levels, how to undo them and what to do when something goes wrong:
**[docs/en/DEDICATED-DEVICE.md](docs/en/DEDICATED-DEVICE.md)**.

## Compatibility

The full table, how it was measured and how to add your phone with a pull request:
**[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)**.

**Status on 2026-11-16, honestly:**

| | |
|---|---|
| CI build, 12 unit tests, lint | ✔ verified (GitHub Actions green) |
| Installs and starts on Android 5.0 → 15 | ✔ verified **on emulators only** |
| First-run guide, PIN, owner menu, native error screens | ✔ verified **on emulators only** (automated taps, not by eye) |
| Official sign-in page renders | ✔ on emulators with **GeckoView 160 (Android 8.0+)**; WebView 83 on an emulator, but **a real phone with an outdated WebView got a broken page**, so the app now asks for WebView 111+ · ✘ WebView ≤ 74 · ✘ Android ≤ 7.0 (certificate not trusted) |
| Signing in, chatting, voice, camera, files, share, downloads | **not verified on a real device** (tests have no account, no microphone, no camera) |
| Home role, assistant, screen pinning, dedicated mode, rest screen | **not verified on a real device** |

Realistic minimum today: **Android 8.0+** with an up-to-date Android System WebView, or the
gecko edition. The app installs on Android 5–7 and explains the problem natively, but the
site very probably cannot load there.

## Signing in

Use **Continue with email** on the Claude page.

- **Google sign-in** is normally refused by Google inside embedded web views
  ("This browser or app may not be secure"). The app does not disguise itself to get around
  that. If your account was created with Google, sign in by email with the same address.
- A sign-in done in a **system browser or Custom Tab cannot be handed to the app**: browsers
  and web views keep separate cookie stores, and moving a session between them would mean
  extracting cookies, which this project never does. So the app does not offer a
  "sign in with the browser" button; links to other sites simply open in the system browser.
- With the **gecko** edition and the GeckoView engine selected, the page runs in a full
  browser engine with its normal user agent. Whether Google accepts sign-in there was **not
  verified**.
- **Company SSO**: add your organisation's sign-in host in the owner menu → *Extra sign-in
  sites*.
- If Claude emails a sign-in **link**, open it on the phone, or copy it and use owner menu →
  **Paste a sign-in link** (only `https://claude.ai/…` links are accepted).

## Using it

| You want to | Do this |
|---|---|
| Talk | Touch the big microphone, speak, touch the page's send button. Say only "new chat", "open projects" or "back" to navigate. |
| Use Claude's own voice mode | Use the voice button inside the page; the app passes the microphone to the page. |
| Attach a photo | Touch **Camera**, take the photo, then touch the attach (+) button **in the page**: the photo is added without asking again. Or touch the page's attach button first and choose Camera / Photos / Files. |
| Send something from another app | Share → OldPhoneClaude. Text opens a new chat with the text filled in; files wait until you touch the page's attach button. |
| Reload | Pull down from the top edge of the page until "Release to reload" appears, or owner menu → Reload. |
| Open the owner menu | Keep the microphone pressed for 3 seconds, enter your PIN. With TalkBack: the microphone button has an "Owner menu" action. |
| Wake the rest screen | Touch it, or use the assistant gesture. |

The address claude.ai/new accepts a `q` parameter that pre-fills the message box
([Anthropic help article](https://support.claude.com/en/articles/14729294-open-claude-desktop-with-a-link));
that documented link is what share-in uses for text.

## Fragile by design: the three "page tools"

The app runs exactly three small functions inside the page, on demand
([`app/src/main/assets/pagetools/lib.js`](app/src/main/assets/pagetools/lib.js)):

1. **insertText**: types dictated text into the message box.
2. **lastReply**: returns the text to read aloud (your selection first, otherwise the last
   reply it can find).
3. **probe**: counts how much of the page rendered, to detect a blank page.

They depend on how the page is built today and **can break whenever the site changes**. They
never hide or change anything and nothing they read leaves the phone. Dictation and
read-aloud can be turned off in the owner menu; when dictation cannot find the message box,
the text is copied so you can paste it.

## Troubleshooting

| Problem | What to try |
|---|---|
| "The web engine is too old" / "The page did not appear" | Play Store → update **Android System WebView** and **Chrome**. If no update is offered, that Android version has reached the end for this engine: on Android 8+ install the **gecko** edition and choose GeckoView in the owner menu. |
| Google sign-in shows an error | Expected. Go back and use **Continue with email**. |
| "Secure connection failed" | Set the correct **date and time**; update the web engine. |
| "Verifying you are human" appears again and again | That is the site's bot check. Complete it; if it never passes, the engine is probably too old for it. The app does not bypass it. |
| The microphone button says voice typing is not available | Install/enable a speech recognition service (for example Google's "Speech Recognition & Synthesis") and allow the microphone. |
| Dictated text does not appear in the box | The helper could not find the box (the site changed). The text is in the clipboard: touch and hold the box → Paste. Please open an issue. |
| "App not installed" when updating | The new APK has a different signature (debug builds). Uninstall, reinstall, sign in again. See [INSTALL](docs/en/INSTALL.md#debug-builds). |
| Photo or file does not attach | After taking the photo, touch the attach button in the page. Files over 30 MB are not accepted from Share. |
| The phone is slow or the page reloads by itself | Old phones have little memory: disable unused apps, restart the phone, turn on *Reduce animations*. |
| Forgot the owner PIN | Android *Settings → Apps → OldPhoneClaude → Storage → Clear data* resets the app (and signs you out). In dedicated mode that is not reachable: see [the way out](docs/en/DEDICATED-DEVICE.md#26-how-to-undo-it). |
| Stuck in dedicated mode | [docs/en/DEDICATED-DEVICE.md](docs/en/DEDICATED-DEVICE.md#26-how-to-undo-it) |

## Known limits

- **Not tested on a real phone by the author.** See the status table above and
  [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **Downloads:** with the WebView engine, ordinary https downloads are handed to the system
  browser (which may not be signed in) and files generated inside the page (`blob:`
  downloads, such as exported artifacts) are **not supported**. With GeckoView, downloads
  are saved to the Downloads folder.
- **Notifications** from the site are not supported (the system WebView has no web
  notifications; they are declined in GeckoView).
- **Full-screen video** and **location** requests are declined.
- The bar hides on scroll and returns after a few quiet seconds; pages that do not scroll
  are unaffected.
- Screen pinning ends at reboot (Android behaviour). Dedicated mode does not.
- The rest screen cannot know whether the page's voice mode is in use; touch the screen to
  keep it awake during long voice sessions, or turn the rest screen off.
- An old phone means an **old, unpatched Android**. See [SECURITY.md](SECURITY.md).

## Battery and safety

Old phones that stay plugged in all day deserve respect.

- **A swollen, damaged or very hot battery is dangerous** (fire, toxic fumes). Signs: the
  back cover bulges, the screen lifts or shows pressure marks, the phone rocks on a flat
  table, it gets hot while idle. **Stop using it, unplug it, do not press or puncture it,**
  and take it to an electronics recycling point.
- If the phone has a **removable battery** and still powers on from the charger without it,
  running it without the battery removes the risk. Many phones do not start without a
  battery; that is normal.
- If the phone's own settings offer **charge limiting** ("Protect battery", "Limit to 80/85%",
  "Battery care"), turn it on. Do not use anything that needs root.
- Otherwise, a plain **mains timer** that powers the charger for a few hours a day keeps the
  battery away from 100% all the time.
- Use the original charger or a certified one, a healthy cable, a **hard, non-flammable
  surface**, away from sun, heaters, beds and curtains. Remove thick cases.
- **Do not leave it charging unattended for days**, and check the battery every few weeks.
- Lower brightness and the dark rest screen reduce heat and OLED burn-in.

## Privacy

- The app has no server and sends nothing anywhere. It asks only for the permissions you
  see: internet, microphone, camera, start at boot.
- Your session lives in the web engine's own storage on the phone, like in a browser, and is
  excluded from Android backups.
- Photos and shared files waiting to be attached sit in the app's private cache and are
  deleted after a day.
- The owner PIN is stored only as a salted hash.
- What you do on claude.ai is governed by Anthropic's terms and privacy policy.

## Building and releasing

No Mac and no local setup are required: **GitHub Actions builds everything.**

| Workflow | When | What |
|---|---|---|
| [Build](.github/workflows/build.yml) | every push and pull request | unit tests, lint, all APKs as an artifact, SHA-256 in the run summary |
| [Release](.github/workflows/release.yml) | every `v*` tag | GitHub Release with the APKs, `SHA256SUMS.txt`, and the fingerprints in the notes |
| [Compatibility probe](.github/workflows/emulator.yml) | by hand | boots Android emulators and records whether the app starts and the site renders |

The signing key lives **only in GitHub Secrets**, never in the repository. Without the
secret, CI builds **debug** APKs and says so. How to create the key without a Mac:
**[docs/en/SIGNING.md](docs/en/SIGNING.md)**.

Locally (optional): JDK 21 + Android SDK, then `./gradlew :app:assembleLiteDebug`.

F-Droid: assessed, nothing submitted. See [docs/en/FDROID.md](docs/en/FDROID.md).

## Project layout

```
app/src/main/java/.../oldphone/
  MainActivity.kt        the device screen: engine, bar, voice, attachments, errors, rest
  SetupActivity.kt       first-run guide
  OwnerActivity.kt       owner menu            PinPrompt.kt / Pin.kt   owner PIN
  Voice.kt               dictation, commands, read aloud
  Kiosk.kt               device owner / lock task        App.kt   crash restart, boot
  engine/UrlPolicy.kt    which links stay inside (unit tested)
  engine/SystemWebViewEngine.kt   Android System WebView
app/src/gecko/           GeckoView engine (gecko edition only)
app/src/main/assets/pagetools/lib.js   the only code run inside the page
docs/                    guides (en, it) and the compatibility table
.github/workflows/       build, release, emulator probe
```

## Licence

[Apache License 2.0](LICENSE). Chosen over MIT because it is just as permissive but adds an
explicit **patent grant** and states plainly that it gives **no rights to trademarks**, which
matters for a project whose name mentions someone else's product. It is also compatible
with GeckoView's MPL-2.0. See [NOTICE](NOTICE).

## Contributing

Device reports are the most valuable contribution: [CONTRIBUTING.md](CONTRIBUTING.md) ·
[Code of Conduct](CODE_OF_CONDUCT.md) · [Security](SECURITY.md).
