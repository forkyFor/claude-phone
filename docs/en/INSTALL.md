# How to install the APK (and how to update it)

*Italiano: [docs/it/INSTALL.md](../it/INSTALL.md)*

No computer and no developer account are needed. Everything happens on the old phone.

> OldPhoneClaude is an independent project, not affiliated with Anthropic. You need your own
> Claude account. No API key is needed or used.

## Before you start

1. **Charge the phone and check the battery.** If the back of the phone is bulging, the
   screen is lifting, or the phone gets hot while charging, **stop**: do not use that phone.
   See [Battery safety](../../README.md#battery-and-safety).
2. **Connect the phone to Wi-Fi.**
3. **Update what can be updated** (this matters a lot on old phones):
   - *Settings → System → System update* (or *About phone → Software update*).
   - Open the **Play Store**, search **Android System WebView** and touch **Update**. Do the
     same for **Google Chrome**. This is the web engine the app uses.

## Which file do I need?

Open the project's **Releases** page in the phone's browser:
`https://github.com/forkyFor/claude-phone/releases`

| File name contains | For | Size |
|---|---|---|
| `lite` | Any phone with Android 5.0 or newer. **Start with this one.** | well under 1 MB (signed release) |
| `gecko-arm64-v8a` | Android 8.0+, 64-bit phones (most phones from 2017 on), when the lite edition says the web engine is too old | large (about 100 MB or more) |
| `gecko-armeabi-v7a` | Android 8.0+, 32-bit phones | large |

If the file name ends in `-debug`, read [Debug builds](#debug-builds) below.

## Install, step by step

1. On the Releases page, under **Assets**, touch the file you chose. The browser may warn
   that "this type of file can harm your device": touch **Download anyway / OK**.
2. When the download ends, touch **Open** (or pull down the notification bar and touch the
   downloaded file; or open the **Files / Downloads** app and touch it there).
3. Android now blocks the installation because the app does not come from the Play Store.
   Allow it once:
   - **Android 8 and newer:** a message says *"For your security, your phone is not allowed
     to install unknown apps from this source"*. Touch **Settings**, turn on **Allow from
     this source**, then touch **Back**.
   - **Android 5, 6 and 7:** a message says *"Install blocked"*. Touch **Settings**, turn on
     **Unknown sources**, confirm with **OK**, then open the downloaded file again.
4. Touch **Install**. If *Play Protect* says it does not recognise the app, touch
   **Install anyway** (or *More details → Install anyway*).
5. Touch **Open**. The app guides you from here: language, Wi-Fi, permissions, PIN, Home
   screen, sign-in.
6. Recommended: go back to the setting of step 3 and turn it **off** again.

### Checking the download (optional)

Each release lists the **SHA-256** of every APK. To compare on the phone, any "hash checker"
file manager works; on a computer: `sha256sum file.apk` (Linux), `shasum -a 256 file.apk`
(macOS), `certutil -hashfile file.apk SHA256` (Windows).

## Signing in

Choose **Continue with email** on the Claude page and follow its instructions.

- **Sign-in with Google usually fails inside this kind of app.** Google blocks sign-in from
  embedded web views; that is Google's rule and this app does not work around it. Use the
  email option, even if your account was created with Google: use the same email address.
- If Claude sends you a **link** by email: open the email on the old phone and touch the
  link, then choose OldPhoneClaude if Android asks which app to use. If Android does not
  ask, copy the link (touch and hold it → *Copy link*), then in the app keep the microphone
  button pressed for 3 seconds → enter your PIN → **Paste a sign-in link**.
- If Claude shows a **code** to type instead, type it in the page.
- A link opened on a different device signs in that device, not the phone.

## Updating the app

The app never updates by itself and never checks for updates.

1. Keep the microphone button pressed for 3 seconds → PIN → **Check for updates**, or open
   the Releases page in a browser.
2. Download the newer APK **of the same edition** and install it as above. Your sign-in and
   settings are kept.
3. If the phone is in **dedicated mode**, turn dedicated mode off first (owner menu), update,
   then turn it on again.

If Android says **"App not installed"** or *"package conflicts with an existing package"*,
the new APK was signed with a different key than the installed one (see below). The only way
is to uninstall the old one first, which signs you out.

## Debug builds

If the repository has no signing key configured, GitHub Actions can only produce **debug**
APKs. The release notes say so in the first line and the file names end in `-debug`.

- They work normally, but they are bigger and slower than release builds.
- **Each build is signed with a different throw-away key**, so a newer debug APK cannot be
  installed over an older one: uninstall first, then sign in again.
- For daily use, the maintainer should configure signing: [SIGNING.md](SIGNING.md).

## Uninstalling

- If the app is the Home screen: *Settings → Apps → Default apps → Home app*, choose the
  phone's original launcher, then uninstall normally.
- If the app is **device owner** (dedicated mode): owner menu → **Give up device owner**
  first. Until then Android refuses to uninstall it.
