# Publishing on F-Droid: assessment

*Italiano: [docs/it/FDROID.md](../it/FDROID.md)*

**Nothing has been submitted to F-Droid.** This page is an assessment and a checklist for
whoever maintains the project. It reflects our reading of F-Droid's inclusion policy; the
F-Droid reviewers decide.

## Lite edition: probably eligible

| Requirement | Status |
|---|---|
| Free licence | Apache-2.0 |
| No proprietary libraries | None. The lite edition has **no dependencies at all** besides the Kotlin standard library; no Google Play Services, Firebase, analytics or ads. |
| Builds from source with free tools | Gradle + Android Gradle Plugin, sources and plugins from Maven Central / Google Maven. |
| No dependency metadata blob | `dependenciesInfo { includeInApk = false }` is set. |
| Store metadata | `fastlane/metadata/android/{en-US,it-IT}` (title, descriptions, changelog). Screenshots are still missing. |
| Version tags | `vX.Y.Z` tags matching `versionName`; `versionCode` must increase each release. |

Things a reviewer will raise:

- **Anti-feature "NonFreeNet".** The app is only useful with claude.ai, a proprietary network
  service that requires an account. F-Droid accepts such apps but labels them.
- **Name and trademark.** "Claude" is Anthropic's trademark. The name, description and icon
  must make clear this is unofficial; F-Droid (or Anthropic) may still ask for a different
  name. The name is defined in one place: `app_name` in
  `app/src/main/res/values/strings.xml`.
- **System WebView.** The lite edition relies on Android System WebView, which on most
  phones is a proprietary Google build. This is normal for F-Droid apps and not an
  anti-feature by itself.
- **Speech recognition** uses whatever recognition service the phone has (often Google's).
  It is optional and off the app's own network path; worth a line in the description.

## Gecko edition: probably not eligible as it is

The gecko edition downloads **prebuilt GeckoView binaries** from `maven.mozilla.org`.
GeckoView is free software (MPL-2.0), but F-Droid normally requires binaries to come from
its list of trusted repositories or to be built from source, and building Gecko from source
is a very large job. We have not confirmed how F-Droid treats this repository today. The
realistic plan is to submit only the lite edition (`gradle: [lite]`) and keep distributing
the gecko edition through GitHub Releases.

## Draft metadata (for the fdroiddata repository)

```yaml
Categories:
  - Internet
License: Apache-2.0
SourceCode: https://github.com/forkyFor/claude-phone
IssueTracker: https://github.com/forkyFor/claude-phone/issues
AutoName: OldPhoneClaude
AntiFeatures:
  NonFreeNet:
    en-US: Only works with the proprietary claude.ai service and an account there.

RepoType: git
Repo: https://github.com/forkyFor/claude-phone.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - lite

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

## Checklist before submitting

- [ ] At least one tagged release, tested on a real phone.
- [ ] Decide the final name; check it against Anthropic's brand guidelines.
- [ ] Add screenshots to `fastlane/metadata/android/*/images/phoneScreenshots/`.
- [ ] Run `fdroid lint` and `fdroid build` on the metadata locally.
- [ ] Open a merge request on `gitlab.com/fdroid/fdroiddata` (or a "Request For Packaging").
- [ ] Optional: reproducible builds, so F-Droid can ship the APK signed with the project's
      own key and users can switch between GitHub and F-Droid builds without reinstalling.
