# Compatibility · Compatibilità

*English first, italiano più sotto. The tables are shared.*

## Short answer

- **The app installs on Android 5.0+** (lite edition) and always explains natively when the
  site cannot work.
- **The lowest setup on which the official sign-in page was actually seen rendering** is:
  - **GeckoView 160 on Android 8.0** (gecko edition), and
  - **Android System WebView 83** on an Android 11 emulator. **A real phone contradicted
    this:** on a Redmi Note 7 with an outdated WebView the page came up half-styled with the
    site's own "browser is out of date" notice. The app now treats **WebView below 111**, or
    any WebView failing its capability check, as too old.
- **WebView 74 and older cannot work**: the site's bot check itself fails with a JavaScript
  syntax error or stays blank.
- **Android 7.0 and older very probably cannot work at all**: on stock Android 5.0, 6.0 and
  7.0 images the connection to claude.ai fails with an *untrusted certificate*, and the
  system WebView relies on Android's own certificate store, which updating the WebView does
  not change. GeckoView would bring its own certificates but needs Android 8.0.
- **Everything after sign-in is unverified.** The probe has no account. Chats, projects,
  voice, uploads and downloads inside the signed-in site were not tested by the author.

So, realistically: **Android 8.0 or newer**, with either an up-to-date system WebView or the
gecko edition. Android 7.1 is an open question. Reports welcome.

## How this was measured

No real phone was available. The author used **stock Android emulators on GitHub Actions**
([workflow](../.github/workflows/emulator.yml), [`scripts/probe.sh`](../scripts/probe.sh),
[`scripts/ui-walk.py`](../scripts/ui-walk.py)), run on 2026-11-16:

1. **UI walk.** The debug APK is installed and the first-run guide is tapped through with
   real input events: every step, the owner PIN, the main screen, the hidden gesture or the
   error screen's button, the PIN dialog, the owner menu, and back.
2. **Site probe.** The app opens `https://claude.ai/login` and logs, every 12 seconds, counts
   of what the engine rendered (text length, elements, form fields, buttons), the HTTP
   status, JavaScript console errors, and whether a bot-check page is showing.

Limits, stated plainly:

- **Emulators are not phones.** Speed, memory, cameras, microphones, vendor skins and
  battery behaviour were not tested.
- **Stock images carry the WebView that Android version shipped with**, not the newest one a
  real phone can install from the Play Store. A real Android 9 phone can have WebView 138;
  the Android 9 emulator has 66.
- **No sign-in.** Only the sign-in page was observed.
- **The site's bot check ("Verify you are human") appears intermittently** on the data-centre
  network of the test machines and nobody is there to complete it. The app never tries to
  get past it. Runs that ended on that page are marked "bot check" and prove nothing either
  way about the engine, except where the check itself crashed.
- Screenshots were saved as workflow artifacts but could not be viewed by the author; the
  layout was checked through the accessibility tree only, not by eye.

## Measured: emulators

`UI walk`: ✔ = all native screens reached. `Site`: what the engine did with the sign-in page.

| Android (API) | Engine | UI walk | Site (sign-in page) | Verdict |
|---|---|:-:|---|---|
| 5.0 (21) | stock WebView 37 | n/a¹ | TLS: certificate not trusted | ✘ |
| 6.0 (23) | stock WebView 44 | ✔ (run 2: guide and error screen) | TLS: certificate not trusted | ✘ |
| 7.0 (24) | stock WebView 52 | ✔ | TLS: certificate not trusted | ✘ |
| 7.1.1 (25) | stock WebView 52 | ✔ | `ERR_SPDY_PROTOCOL_ERROR` (engine too old; certificate accepted) | ✘ with this WebView |
| 8.0 (26) | stock WebView 58 | native "No web engine" screen shown² | WebView did not start on this image | ✘ on this image |
| 8.0 (26) | **GeckoView 160** | – | run 2: **rendered** (3234 chars, 5 fields, 25 buttons, 0 JS errors); run 3: bot check | **✔ sign-in page** |
| 8.1 (27) | stock WebView 61 | ✔ | HTTP 403, blank, bot check cannot run | ✘ |
| 9 (28) | stock WebView 66 | ✔ up to the PIN dialog³ | HTTP 403, blank, bot check cannot run | ✘ |
| 9 (28) | **GeckoView 160** | – | run 2: **rendered** (3216 chars, 28 buttons); run 3: bot check | **✔ sign-in page** |
| 10 (29) | stock WebView 74 | ✔ | blank, `SyntaxError: Unexpected token .` in the bot check | ✘ |
| 11 (30) | stock **WebView 83** | ✔ | run 2: bot check passed by itself, then **rendered** (4224 chars, 22 buttons); run 3: bot check | **✔ sign-in page (once)** |
| 11 (30) | **GeckoView 160** | – | **rendered** in both runs (3216 chars, 36 buttons, 0 JS errors) | **✔ sign-in page** |
| 12 (31) | stock WebView 91 | ✔ | bot check (not completed) | inconclusive |
| 13 (33) | stock WebView | emulator job failed to start | – | not measured |
| 14 (34) | stock WebView 113 | ✔ bar + hidden gesture | bot check (not completed) | inconclusive |
| 14 (34) | **GeckoView 160** | – | **rendered** (3216 chars, 36 buttons, 0 JS errors) | **✔ sign-in page** |
| 15 (35) | stock WebView 124 | ✔ bar + hidden gesture | bot check (not completed) | inconclusive |
| 15 (35) | **GeckoView 160** | – | **rendered** in all three runs | **✔ sign-in page** |

¹ The Android 5.0 image returned an empty accessibility tree, so the walk could not run; the
app installed and its process was alive, no crash was logged.
² Creating the WebView threw an exception on this emulator image. The app caught it and
showed its native "No web engine found" screen, which is the intended behaviour.
³ The walk script could not find the dialog's OK button on this image (label reported
differently); the PIN dialog itself opened.

On every image: install succeeded, the app process stayed alive, and **no fatal exception**
was logged.

## Not measured: what a real phone can update to

The newest system WebView / Chrome each Android version can receive, from public
announcements (not verified by the author):

| Android | Newest WebView/Chrome | Newest GeckoView | Expectation |
|---|---|---|---|
| 5.0 – 5.1 | about 95 | 143 (not shipped by this project) | ✘ certificate store too old (measured on stock images) |
| 6.0 | about 106 | 143 (not shipped) | ✘ same |
| 7.0 | about 119 | 143 (not shipped) | ✘ same |
| 7.1 | about 119 | 143 (not shipped) | ? engine new enough on paper; certificate accepted on the emulator. **Untested.** |
| 8.0 – 9 | 138 ([Chrome 139 needs Android 10](https://9to5google.com/2025/06/26/google-chrome-android-versions-no-longer-supported-2025/)) | current ([Firefox 144+ needs Android 8](https://blog.mozilla.org/futurereleases/2025/09/15/raising-the-minimum-android-version-for-firefox/)) | ✔ expected with WebView 138 or the gecko edition. **Untested on real phones.** |
| 10 and newer | current | current | ✔ expected |

GeckoView 143 was the last to support Android 5–7. This project does not ship it: it would
be an engine frozen without security fixes, and it would not solve the certificate problem
any more reliably than testing on a real phone would show.

## Real devices

One so far. Add yours with the *Device report* issue or a pull request adding a row here.
Only write what you actually tried.

| Phone (year) | Android | Edition, engine + version | Sign-in | Chat | Voice (page / native) | Camera, files | Home / pin / dedicated | Notes | Reported by |
|---|---|---|---|---|---|---|---|---|---|
| Xiaomi Redmi Note 7 (2019) | 10 (MIUI 12.5.1, patch 2021-08) | lite 0.1.0 debug, system WebView **not updated** (version not recorded, at least 83) | ✘ page half-styled, sign-in form missing, site notice "your browser or device software is out of date" | – | – | – | – | App starts, native bar shown. Led to the capability check in setup and the 111 threshold. Retest pending after updating WebView. | project owner |
| *your phone here* | | | | | | | | | |

## Feature status

| Feature | Status |
|---|---|
| Build in GitHub Actions, unit tests (12), lint | **Verified** (CI green) |
| App installs and starts, Android 5.0 – 15 | **Verified on emulators** · not on real devices |
| First-run guide, owner PIN, owner menu, hidden gesture | **Verified on emulators** (UI walk) · not by eye, not on real devices |
| Native "engine too old" / "no engine" screens | **Verified on emulators** |
| Sign-in page renders (WebView ≥ 83, GeckoView 160) | **Verified on emulators**, subject to the bot check |
| Signing in, staying signed in, chatting | Not verified (no account in tests) |
| Google sign-in blocked in WebView | Not verified here; documented behaviour of Google |
| Page voice mode (getUserMedia), native dictation, read aloud | Not verified (emulators have no usable audio in CI) |
| Phone voice commands: parsing (EN/IT), number extraction, contact matching | **Verified** by unit tests |
| Phone voice commands on a device: help, time, call confirmation and countdown, cancel, a real call placed after the countdown, open settings | **Verified on emulators** Android 6.0 → 15 (10 images, 2026-11-17; the sentence is handed over without a microphone, `scripts/voice-walk.py`) · **not verified on a real device** |
| Contacts lookup, messages, flashlight, volume, calls in dedicated mode | Not verified |
| Meetings: text of NotebookLM's answer → title/description/summary, note file, project address, store | **Verified** by unit tests |
| Meetings on a device: screen, record/stop/save, simulated answer, audio deleted after accepting | Emulator check written (`scripts/voice-walk.py`); **not verified on a real device** |
| Meetings with NotebookLM and a Claude project (share of the audio, project creation, upload) | Not verified: needs a real phone with NotebookLM and a signed-in account |
| Camera capture, file picker, share-in | Not verified |
| Downloads | Not verified; WebView limits are by design, see README |
| Home role, assistant role, boot start | Not verified |
| Screen pinning, dedicated mode (device owner) | Not verified on any device |
| Rest screen, dimming, network recovery | Not verified |
| TalkBack | Labels and actions are set in code; not tested with TalkBack running |
| Release workflow (tag → GitHub Release) | **Verified**: v0.1.0 published with three debug APKs and `SHA256SUMS.txt` |

---

## Italiano

### In breve

- **L'app si installa da Android 5.0** (edizione lite) e spiega sempre con una schermata
  nativa quando il sito non può funzionare.
- **La configurazione più bassa su cui la pagina di accesso ufficiale è stata davvero vista
  comparire** è **GeckoView 160 su Android 8.0** (edizione gecko) e **Android System WebView
  83** (visto su un emulatore Android 11).
- **WebView 74 e precedenti non possono funzionare**: la verifica anti-bot del sito fallisce
  con un errore di sintassi JavaScript o resta bianca.
- **Android 7.0 e precedenti molto probabilmente non possono funzionare**: sulle immagini di
  serie di Android 5.0, 6.0 e 7.0 la connessione a claude.ai fallisce per *certificato non
  attendibile*, e il WebView di sistema usa l'archivio certificati di Android, che
  l'aggiornamento del WebView non cambia. GeckoView porterebbe i propri certificati, ma
  richiede Android 8.0.
- **Tutto ciò che viene dopo l'accesso non è verificato**: le prove non hanno un account.

Quindi, realisticamente: **Android 8.0 o successivo**, con WebView di sistema aggiornato o
con l'edizione gecko. Android 7.1 resta una domanda aperta.

### Come è stato misurato

Non era disponibile alcun telefono reale. Sono stati usati **emulatori Android di serie su
GitHub Actions** (16 novembre 2026): un percorso automatico della guida iniziale con tocchi
reali, e una sonda che apre `https://claude.ai/login` e registra quanta pagina viene
disegnata, lo stato HTTP e gli errori JavaScript.

Limiti, detti chiaramente: gli emulatori non sono telefoni; le immagini di serie hanno il
WebView con cui quella versione di Android è uscita, non il più recente installabile dal
Play Store; nessun accesso all'account; la verifica anti-bot del sito compare a intermittenza
sulla rete dei server di prova e l'app non tenta mai di superarla (le prove finite su quella
pagina sono "inconcludenti"); gli screenshot non sono stati guardati dall'autore, la verifica
è avvenuta solo tramite l'albero di accessibilità.

Le tabelle qui sopra valgono per entrambe le lingue. "Verified on emulators" = verificato su
emulatore; "Not verified" = **non verificato su dispositivo reale** né su emulatore.

### Dispositivi reali

Uno finora. Aggiungi il tuo con la issue *Device report* o con una pull request che
aggiunge una riga alla tabella "Real devices". Scrivi solo ciò che hai davvero provato.
