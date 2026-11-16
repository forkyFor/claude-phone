# Signing the APKs without a Mac (or any developer account)

*Italiano: [docs/it/SIGNING.md](../it/SIGNING.md)*

Android only installs an update over an existing app when both were signed with the **same
key**. This project never keeps that key in the repository: it lives in **GitHub Secrets**
and is used only by GitHub Actions.

| Situation | What CI produces |
|---|---|
| Secrets configured | Signed, optimised **release** APKs. Updates install over each other. |
| No secrets (default for a fresh fork) | **Debug** APKs, named `-debug`, and the build summary and release notes say so. Each build has a different throw-away key. |

## 1. Create the key (once)

You need the `keytool` program, which comes with Java. Two ways that need no Mac:

**A. In the browser, with GitHub Codespaces** (free quota, private to your account): on the
repository page, *Code → Codespaces → Create codespace*. In the terminal at the bottom:

```bash
keytool -genkeypair -v -keystore release.jks -alias oldphone -keyalg RSA -keysize 4096 -validity 10000
```

**B. On any Windows or Linux PC** with a JDK installed (for example Temurin): the same
command in a terminal.

`keytool` asks for a password (choose a long one, write it down) and a few identity
questions (any answer is fine; they are visible to anyone who inspects the APK).

## 2. Turn the key into text

```bash
base64 -w0 release.jks
```

(On macOS: `base64 -i release.jks`. On Windows PowerShell:
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`.)

Copy the long line it prints.

## 3. Store it in GitHub Secrets

Repository → **Settings → Secrets and variables → Actions → New repository secret**. Create
four secrets:

| Name | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the long line from step 2 |
| `SIGNING_STORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | `oldphone` (the alias used in step 1) |
| `SIGNING_KEY_PASSWORD` | the key password (same as the keystore password unless you chose another) |

## 4. Keep a backup, then clean up

- **Download `release.jks` and keep it somewhere safe and offline**, together with the
  password. GitHub never shows a secret again. If the key is lost, every user has to
  uninstall and reinstall (and sign in again) to get future versions.
- Then delete it from the Codespace (`rm release.jks`) and delete the Codespace.
- Never commit the key. `.gitignore` already blocks `*.jks` and `*.keystore`.

## 5. Publish a release

1. Set `versionName` (and increase `versionCode`) in `app/build.gradle.kts`, commit.
2. Create a tag with the same version, starting with `v`:
   - with git: `git tag v0.1.0` then `git push origin v0.1.0`; or
   - on the website: *Releases → Draft a new release → Choose a tag → type `v0.1.0` →
     Create new tag → Publish release*.
3. The **Release** workflow builds the APKs and attaches them to the release together with
   `SHA256SUMS.txt`. The release notes list the SHA-256 of each APK and of the signing
   certificate.

## Notes

- Secrets are not available to pull requests from forks, so those always build debug APKs.
  That is intended.
- The key file is written to the runner's temporary folder for the duration of the build and
  deleted in an `always()` step.
- Verifying a downloaded APK on a computer with the Android build tools:
  `apksigner verify --print-certs claude-phone-x.y.z-lite.apk`.
