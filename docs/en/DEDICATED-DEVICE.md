# Making the phone a dedicated Claude device

*Italiano: [docs/it/DEDICATED-DEVICE.md](../it/DEDICATED-DEVICE.md)*

There are two levels. Start with the basic one; most people never need the second.

| | Basic level | Dedicated-device level |
|---|---|---|
| Needs a computer | No | Yes, once (adb) |
| Needs a factory reset | No (recommended) | **Yes**, and no accounts on the phone |
| Home button returns to Claude | Yes | Yes |
| Status bar and notifications | Hidden, come back with a swipe | Blocked |
| Leaving the app | Possible for someone who knows the gesture | Only with the owner PIN |
| Risk | None | A mistake or a forgotten PIN can require a factory reset |

> **Not verified on a real device.** Everything on this page was written from the Android
> documentation and checked for compilation only. The device-owner commands could not be run
> on a physical phone by the author. Try it on a phone you can afford to reset, and please
> report what you find (issue template "Device report").

No step here uses root, unlocks the bootloader, changes system partitions, replaces the boot
animation or lock screen, or turns off any Android protection.

---

## 0. Prepare the phone (both levels)

1. **Back up anything you want to keep**, then reset: *Settings → System → Reset options →
   Erase all data (factory reset)*. On older versions: *Settings → Backup & reset → Factory
   data reset*.
2. Go through Android's first-time setup and connect to Wi-Fi.
   - **Basic level:** you may sign in with a Google account, so the Play Store can update the
     web engine.
   - **Dedicated level:** you will need the phone to have **no accounts** at step 2.3. The
     easiest order is: sign in to Google, do the updates of step 3 here, then remove the
     account (*Settings → Accounts → your account → Remove account*).
3. **Update what can be updated:**
   - *Settings → System → System update*.
   - Play Store → **Android System WebView** → Update. Also update **Google Chrome**.
   - For voice typing: Play Store → **Speech Recognition & Synthesis** (or "Speech Services
     by Google") → Update, and in the **Google** app download your language for offline use
     if offered.
4. **Disable apps you do not need** (less memory, fewer notifications): *Settings → Apps →*
   touch an app → **Disable** (or Uninstall). Typical candidates: social apps, games, the
   manufacturer's store, news, duplicate browsers. Do **not** disable: Android System
   WebView, Google Play services, the keyboard, Settings, the Camera, Files/Documents, and
   the speech services if you want voice typing. No root is needed; if an app cannot be
   disabled, leave it.
5. *Settings → Display → Sleep*: any value is fine. The app keeps the screen on by itself
   **only while the phone is charging**.
6. Install claude-phone ([INSTALL.md](INSTALL.md)) and complete its guide, including the
   **owner PIN**. Write the PIN down.

---

## 1. Basic level (no computer)

### 1.1 Home screen

During the app's guide choose **Make it the Home screen**. Later: owner menu → **Choose the
Home app**. From then on the Home button, and a restart of the phone, lead to Claude.

To undo: *Settings → Apps → Default apps → Home app* → choose the original launcher.

### 1.2 Assistant gesture (optional)

Owner menu → **Assistant app settings** → *Digital assistant app* (or *Assist app*) →
claude-phone. The phone's assistant gesture (long press on Home; on some phones a corner
swipe or a long press on the power button) then starts listening.

There is deliberately **no "Hey Claude"**: the phone never listens with the screen off or in
the background. Use the big button or the system gesture.

### 1.3 Screen always on while charging

Nothing to do: owner menu → *Screen* has **Keep the screen on while charging** (on by
default) and the **dark rest screen**. After 1 minute without touches the screen dims; after
3 minutes a black screen with a small clock appears and moves a little every minute to
protect OLED screens. A touch wakes it.

### 1.4 Screen pinning

Screen pinning is Android's own "stay in this app" feature.

1. Turn it on in Android: *Settings → Security → Screen pinning* (Android 9+: *Security →
   Advanced → App pinning*; Samsung: *Biometrics and security → Other security settings →
   Pin windows*). Also turn on **Ask for PIN/pattern before unpinning** if you want leaving
   to require the phone's screen lock.
2. In the app: keep the microphone pressed 3 seconds → PIN → **Pin the screen (basic
   lock)**. Android asks for confirmation.
3. **To unpin:** press and hold **Back** and **Overview** together. With gesture navigation:
   swipe up from the bottom and hold. Or owner menu → **Unpin the screen**.

Limits of the basic level: pinning ends when the phone restarts, and anyone who knows the
unpin gesture can leave unless you enabled the unlock requirement in step 1.

---

## 2. Dedicated-device level (computer with adb)

Here the app becomes the phone's **device owner**, a role Android reserves for one
management app. Only a device owner may use *lock task mode* without confirmation: no status
bar, no notifications, no Home or Recents, no way out except through the app.

### Read this first

- **It only works on a phone with no accounts and no extra users.** Android refuses
  otherwise. That is why the phone must be freshly reset.
- **If you forget the owner PIN, or the app stops working while dedicated mode is on, the
  only way out may be a factory reset from recovery,** which erases the phone.
- While the app is device owner it **cannot be uninstalled** and the phone cannot be given a
  different device owner.
- Keep USB debugging on until you are sure everything works; it lets you install a fixed
  version of the app from the computer.

### 2.1 Install adb on the computer

Download **SDK Platform-Tools** from `https://developer.android.com/tools/releases/platform-tools`
(Windows, Linux or macOS), unzip it, and open a terminal in that folder. On Linux you can
also install the `adb` package of your distribution.

### 2.2 Enable USB debugging on the phone

1. *Settings → About phone* → touch **Build number** seven times.
2. *Settings → System → Developer options* → turn on **USB debugging**.
3. Connect the phone with a USB **data** cable. On the phone, accept *Allow USB debugging*.
4. On the computer:

```bash
adb devices
```

The phone must be listed as `device` (not `unauthorized`).

### 2.3 Remove every account

*Settings → Accounts* must be empty (Google, Samsung, Xiaomi, email... all of them). Extra
users or a work profile must be removed too. Check from the computer:

```bash
adb shell dumpsys account | grep -c "Account {"
```

It should print `0`.

### 2.4 Make the app device owner

claude-phone must already be installed. Then:

```bash
adb shell dpm set-device-owner io.github.forkyfor.oldphone/.AdminReceiver
```

Expected answer:

```
Success: Device owner set to package io.github.forkyfor.oldphone/.AdminReceiver
```

Common errors:

| Message contains | Meaning | Fix |
|---|---|---|
| `there are already some accounts on the device` | An account is still present | Remove it (2.3). Some manufacturer accounts only go away with a factory reset. |
| `there are already several users on the device` | A second user, guest or work profile exists | Remove it in *Settings → System → Multiple users* |
| `device is already provisioned` / `already set` | Another device owner or a management profile exists | Factory reset, skip accounts, try again |
| `Unknown admin` | The app is not installed, or a typo in the command | Install the app; copy the command exactly |

### 2.5 Turn dedicated mode on

In the app: keep the microphone pressed 3 seconds → PIN → *Home and locking* →
**Dedicated mode** → **Back to Claude**. From now on:

- the app is the Home screen by policy and starts at boot;
- status bar, notifications, Home and Recents are disabled (on Android 9 and newer only the
  power menu remains, so the phone can still be switched off);
- Android Settings, the camera and the file picker can still be opened, but only from the
  app (Settings only from the PIN-protected owner menu);
- links to other websites are not opened at all.

### 2.6 How to undo it

From the least to the most drastic:

1. **Pause:** owner menu → turn **Dedicated mode** off. The phone behaves normally again;
   the app is still device owner and you can turn the mode back on any time.
2. **Give the phone back:** owner menu → **Give up device owner**. The app becomes an
   ordinary app again and can be uninstalled. To get dedicated mode back you need a factory
   reset and adb again.
3. **If the app does not start or you lost the PIN, with USB debugging still on:** install a
   working version over it from the computer (`adb install -r claude-phone-….apk`; it must
   be signed with the same key). `adb uninstall` and `dpm remove-active-admin` do **not**
   work on a device owner installed normally.
4. **Last resort: factory reset from recovery.** Switch the phone off, then hold the key
   combination for your model (often **Power + Volume up**, on Samsung with Home/Bixby as
   well) until the recovery menu appears, and choose *Wipe data / factory reset*. This
   erases everything on the phone. Because the phone has no Google account, it will not ask
   for one afterwards (no "factory reset protection" lock).

### 2.7 Updating the app in dedicated mode

Turn dedicated mode off (2.6, step 1), install the new APK ([INSTALL.md](INSTALL.md)), turn
it on again. Or from the computer: `adb install -r claude-phone-….apk`.

---

## Living with an always-plugged-in old phone

Read [Battery and safety](../../README.md#battery-and-safety). In short: an old battery kept
at 100% all day ages faster and can swell. Check it regularly, keep the phone on a
non-flammable surface away from the sun, and do not leave it charging unattended for days.
