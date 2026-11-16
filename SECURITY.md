# Security · Sicurezza

## English

### Reporting a problem

Please **do not open a public issue** for security problems. Use
**Security → Report a vulnerability** on the GitHub repository (private advisory). Include
the app version, edition (lite/gecko), Android version, and steps to reproduce. You should
get a first answer within about two weeks; this is a volunteer project.

Problems in claude.ai itself are not ours to fix: report them to Anthropic. Problems in the
web engines go to the Android System WebView / Chromium or Mozilla (GeckoView) projects.

### What counts as a security bug here

- Any way to make the app load a page from a host outside the allowlist *inside* the
  embedded engine (`engine/UrlPolicy.kt`).
- Any way for another app or a web page to read the session, the files waiting to be
  attached, or the owner PIN hash.
- Any way to reach the owner menu, Android settings or another app from dedicated mode
  without the owner PIN.
- Anything that makes the app send data anywhere other than the sites the user opens.

### Security model, in short

- The app is a dedicated browser. Sign-in happens in the official page; the session lives in
  the engine's own cookie store, exactly as in a browser. The app never reads, copies,
  exports or transmits cookies or tokens, and has no server.
- No JavaScript interface is exposed to the page. The only scripts the app runs in the page
  are the three functions in `app/src/main/assets/pagetools/lib.js`.
- Backups of app data are disabled, so the session does not leave the phone that way.
- The owner PIN is stored as a salted PBKDF2 hash. It protects against people using the
  phone, not against someone with a computer and the unlocked phone.
- **Old phones no longer receive Android security updates.** The web engine may also be out
  of date. Do not use a dedicated old phone for accounts or data you cannot afford to
  expose, keep the web engine updated, and prefer the gecko edition where the system WebView
  has stopped updating.
- Debug APKs (built without a signing secret) are for testing: anyone can build an APK with
  the same debug permissions model. Use signed releases for daily use.

## Italiano

### Segnalare un problema

Per favore **non aprire una issue pubblica** per i problemi di sicurezza. Usa
**Security → Report a vulnerability** nel repository GitHub (segnalazione privata). Indica
versione dell'app, edizione (lite/gecko), versione di Android e passi per riprodurre. Una
prima risposta arriva di norma entro due settimane: è un progetto di volontari.

I problemi di claude.ai vanno segnalati ad Anthropic; quelli dei motori web ai progetti
Android System WebView / Chromium o Mozilla (GeckoView).

### Cosa consideriamo un bug di sicurezza

- Qualsiasi modo di far caricare *dentro* il motore incorporato una pagina di un sito fuori
  dall'elenco consentito (`engine/UrlPolicy.kt`).
- Qualsiasi modo, per un'altra app o una pagina web, di leggere la sessione, i file in attesa
  di essere allegati o l'hash del PIN.
- Qualsiasi modo di raggiungere il menu del proprietario, le impostazioni di Android o
  un'altra app dalla modalità dedicata senza il PIN.
- Qualsiasi invio di dati verso destinazioni diverse dai siti aperti dall'utente.

### Modello di sicurezza, in breve

- L'app è un browser dedicato. L'accesso avviene nella pagina ufficiale; la sessione resta
  nell'archivio cookie del motore, come in un browser. L'app non legge, copia, esporta né
  trasmette cookie o token, e non ha alcun server.
- Nessuna interfaccia JavaScript è esposta alla pagina. Gli unici script eseguiti nella
  pagina sono le tre funzioni in `app/src/main/assets/pagetools/lib.js`.
- I backup dei dati dell'app sono disattivati.
- Il PIN è salvato come hash PBKDF2 con sale. Protegge da chi usa il telefono, non da chi ha
  un computer e il telefono sbloccato.
- **I telefoni vecchi non ricevono più aggiornamenti di sicurezza di Android**, e anche il
  motore web può essere datato. Non usare un vecchio telefono dedicato per account o dati
  che non puoi permetterti di esporre, tieni aggiornato il motore web e preferisci l'edizione
  gecko quando il WebView di sistema non si aggiorna più.
- Gli APK di debug servono per le prove: per l'uso quotidiano usa release firmate.
