# Contributing · Contribuire

*(Italiano più sotto.)*

Thank you for helping. The most useful contribution needs no programming: **tell us whether
it works on your old phone** (issue template "Device report", or a pull request that adds a
row to [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md)).

## Ground rules (they are what the project is)

These are not negotiable, and pull requests that break them will be declined:

1. **No Anthropic API, no API keys.** The app only shows the official site, where users sign
   in with their own account.
2. **Never touch credentials.** No reading, storing or sending of cookies, tokens or
   passwords; no private endpoints; no scraping of conversations; no bypassing of blocks,
   bot checks, rate limits or sign-in restrictions (including user-agent spoofing).
3. **The site stays the site.** No replacing, hiding or restyling parts of the page. The only
   in-page code is `app/src/main/assets/pagetools/lib.js`; additions there need a very good
   reason, must be optional, and must be documented as fragile.
4. **No servers, analytics, ads or trackers.** No network requests other than the pages the
   user opens.
5. **No root, no system modifications,** nothing that weakens Android's protections.
6. **Small and light.** No new libraries without discussion; the lite APK must stay tiny and
   run on Android 5.0 with little RAM.
7. **Not the official app.** Name, icon and wording must never suggest otherwise.

## Building

You do not need a computer set up for Android: push to a fork and GitHub Actions builds the
APKs (Actions tab → latest run → Artifacts). Locally: JDK 21 and the Android SDK, then

```bash
./gradlew :app:testLiteDebugUnitTest :app:lintLiteDebug :app:assembleLiteDebug
```

The gecko edition: `./gradlew :app:assembleGeckoDebug -Pabi=arm64-v8a`.

## Code

- Kotlin, framework APIs only (no AndroidX), one activity per screen, views built in code
  with the helpers in `Ui.kt`.
- Everything that decides where a link goes lives in `engine/UrlPolicy.kt` and has unit
  tests; add a test with any change there.
- Engine-specific code implements `engine/WebEngine.kt`.
- Run lint; it is configured with warnings as errors.

## Translations

Copy `app/src/main/res/values/strings.xml` to `values-xx/strings.xml`, translate every entry,
add the language code to `localeFilters` in `app/build.gradle.kts`, to the language pickers
in `SetupActivity.kt` and `OwnerActivity.kt`, and the voice phrases in `Voice.kt`. Guides go
in `docs/xx/`.

## Reporting what you tested

Say exactly what you ran and on what: real phone or emulator, model, Android version, web
engine version (shown in the owner menu), edition. "Not tested" is a perfectly good answer
for a row; a guess presented as a fact is not.

---

## Italiano

Grazie per l'aiuto. Il contributo più utile non richiede di programmare: **dicci se funziona
sul tuo vecchio telefono** (modello di issue "Device report", oppure una pull request che
aggiunge una riga a [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md)).

### Regole di base

Non sono negoziabili; le pull request che le violano verranno rifiutate:

1. **Nessuna API di Anthropic, nessuna chiave API.**
2. **Mai toccare le credenziali:** niente lettura, salvataggio o invio di cookie, token o
   password; niente endpoint privati, scraping delle conversazioni o aggiramento di blocchi,
   controlli anti-bot, limiti o restrizioni di accesso (incluso falsificare lo user agent).
3. **Il sito resta il sito:** niente sostituzioni, parti nascoste o restyling. L'unico codice
   nella pagina è `app/src/main/assets/pagetools/lib.js`.
4. **Niente server, analitica, pubblicità o tracker.**
5. **Niente root né modifiche di sistema.**
6. **Piccola e leggera:** nessuna libreria nuova senza discuterne.
7. **Non è l'app ufficiale:** nome, icona e testi non devono mai farlo pensare.

### Compilare

Non serve un computer configurato per Android: fai push su un fork e GitHub Actions compila
gli APK (scheda Actions → ultima esecuzione → Artifacts). In locale servono JDK 21 e Android
SDK, poi il comando indicato sopra.

### Traduzioni

Copia `values/strings.xml` in `values-xx/strings.xml`, traduci ogni voce e segui i passi
elencati nella sezione inglese. Le guide vanno in `docs/xx/`.

### Riportare cosa hai provato

Scrivi esattamente cosa hai eseguito e su cosa: telefono reale o emulatore, modello, versione
di Android, versione del motore web (nel menu del proprietario), edizione. "Non provato" è
una risposta valida; un'ipotesi presentata come un fatto no.
