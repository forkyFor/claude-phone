# Firmare gli APK senza un Mac (né un account sviluppatore)

*English: [docs/en/SIGNING.md](../en/SIGNING.md)*

Android installa un aggiornamento sopra un'app esistente solo se entrambi sono firmati con
la **stessa chiave**. Questo progetto non tiene mai quella chiave nel repository: sta nei
**Secrets di GitHub** ed è usata solo da GitHub Actions.

| Situazione | Cosa produce la CI |
|---|---|
| Secrets configurati | APK di **release** firmati e ottimizzati. Gli aggiornamenti si installano uno sopra l'altro. |
| Nessun Secret (situazione di un fork appena creato) | APK di **debug**, con `-debug` nel nome; il riepilogo della build e le note di release lo dichiarano. Ogni build ha una chiave usa e getta diversa. |

## 1. Crea la chiave (una volta sola)

Serve il programma `keytool`, incluso in Java. Due modi che non richiedono un Mac:

**A. Nel browser, con GitHub Codespaces** (quota gratuita, privato per il tuo account):
nella pagina del repository, *Code → Codespaces → Create codespace*. Nel terminale in basso:

```bash
keytool -genkeypair -v -keystore release.jks -alias oldphone -keyalg RSA -keysize 4096 -validity 10000
```

**B. Su un qualsiasi PC Windows o Linux** con un JDK installato (per esempio Temurin): lo
stesso comando in un terminale.

`keytool` chiede una password (sceglila lunga e scrivila) e alcune domande sull'identità
(qualsiasi risposta va bene; sono visibili a chi ispeziona l'APK).

## 2. Trasforma la chiave in testo

```bash
base64 -w0 release.jks
```

(Su macOS: `base64 -i release.jks`. Su Windows PowerShell:
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`.)

Copia la lunga riga stampata.

## 3. Salvala nei Secrets di GitHub

Repository → **Settings → Secrets and variables → Actions → New repository secret**. Crea
quattro secret:

| Nome | Valore |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | la lunga riga del passo 2 |
| `SIGNING_STORE_PASSWORD` | la password del keystore |
| `SIGNING_KEY_ALIAS` | `oldphone` (l'alias usato al passo 1) |
| `SIGNING_KEY_PASSWORD` | la password della chiave (uguale a quella del keystore, se non ne hai scelta un'altra) |

## 4. Fai una copia di sicurezza, poi pulisci

- **Scarica `release.jks` e conservalo in un posto sicuro e offline**, insieme alla
  password. GitHub non mostra più un secret dopo averlo salvato. Se la chiave va persa, ogni
  utente dovrà disinstallare e reinstallare (e accedere di nuovo) per avere le versioni
  future.
- Poi cancellala dal Codespace (`rm release.jks`) ed elimina il Codespace.
- Non fare mai commit della chiave. `.gitignore` blocca già `*.jks` e `*.keystore`.

## 5. Pubblica una release

1. Imposta `versionName` (e aumenta `versionCode`) in `app/build.gradle.kts`, fai commit.
2. Crea un tag con la stessa versione, che inizi con `v`:
   - con git: `git tag v0.1.0` e poi `git push origin v0.1.0`; oppure
   - dal sito: *Releases → Draft a new release → Choose a tag → scrivi `v0.1.0` → Create new
     tag → Publish release*.
3. Il workflow **Release** compila gli APK e li allega alla release insieme a
   `SHA256SUMS.txt`. Le note elencano lo SHA-256 di ogni APK e del certificato di firma.

## Note

- I secret non sono disponibili alle pull request provenienti da fork, che quindi producono
  sempre APK di debug. È voluto.
- Il file della chiave viene scritto nella cartella temporanea del runner solo per la durata
  della build e cancellato in un passo `always()`.
- Per verificare un APK scaricato, su un computer con gli strumenti Android:
  `apksigner verify --print-certs OldPhoneClaude-x.y.z-lite.apk`.
