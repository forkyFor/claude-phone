# Come installare l'APK (e come aggiornarlo)

*English: [docs/en/INSTALL.md](../en/INSTALL.md)*

Non servono né un computer né un account sviluppatore. Si fa tutto dal vecchio telefono.

> OldPhoneClaude è un progetto indipendente, non affiliato ad Anthropic. Serve un tuo
> account Claude. Non serve e non viene usata alcuna chiave API.

## Prima di cominciare

1. **Carica il telefono e controlla la batteria.** Se il retro è gonfio, lo schermo si
   solleva o il telefono scalda molto in carica, **fermati**: non usare quel telefono. Vedi
   [Batteria e sicurezza](../../README.it.md#batteria-e-sicurezza).
2. **Collega il telefono al Wi-Fi.**
3. **Aggiorna ciò che si può aggiornare** (sui telefoni vecchi conta molto):
   - *Impostazioni → Sistema → Aggiornamento di sistema* (oppure *Info sul telefono →
     Aggiornamento software*).
   - Apri il **Play Store**, cerca **Android System WebView** e tocca **Aggiorna**. Fai lo
     stesso con **Google Chrome**. È il motore web usato dall'app.

## Quale file mi serve?

Apri la pagina **Releases** del progetto nel browser del telefono:
`https://github.com/forkyFor/claude-phone/releases`

| Il nome del file contiene | Per | Dimensione |
|---|---|---|
| `lite` | Qualsiasi telefono con Android 5.0 o successivo. **Comincia da questo.** | ben sotto 1 MB (release firmata) |
| `gecko-arm64-v8a` | Android 8.0+, telefoni a 64 bit (quasi tutti dal 2017 in poi), quando l'edizione lite dice che il motore web è troppo vecchio | grande (circa 100 MB o più) |
| `gecko-armeabi-v7a` | Android 8.0+, telefoni a 32 bit | grande |

Se il nome del file finisce con `-debug`, leggi [Build di debug](#build-di-debug) più sotto.

## Installazione, passo per passo

1. Nella pagina Releases, sotto **Assets**, tocca il file scelto. Il browser può avvisare
   che "questo tipo di file può danneggiare il dispositivo": tocca **Scarica comunque / OK**.
2. A download finito tocca **Apri** (oppure abbassa la barra delle notifiche e tocca il file
   scaricato; oppure apri l'app **File / Download** e toccalo da lì).
3. Android blocca l'installazione perché l'app non arriva dal Play Store. Consentila una
   volta:
   - **Android 8 e successivi:** compare *"Per motivi di sicurezza, il telefono non è
     autorizzato a installare app sconosciute da questa origine"*. Tocca **Impostazioni**,
     attiva **Consenti da questa origine**, poi tocca **Indietro**.
   - **Android 5, 6 e 7:** compare *"Installazione bloccata"*. Tocca **Impostazioni**, attiva
     **Origini sconosciute**, conferma con **OK**, poi riapri il file scaricato.
4. Tocca **Installa**. Se *Play Protect* dice di non riconoscere l'app, tocca **Installa
   comunque** (o *Altri dettagli → Installa comunque*).
5. Tocca **Apri**. Da qui ti guida l'app: lingua, Wi-Fi, permessi, PIN, schermata Home,
   accesso.
6. Consigliato: torna all'impostazione del punto 3 e **disattivala** di nuovo.

### Verificare il download (facoltativo)

Ogni release elenca lo **SHA-256** di ogni APK. Per confrontarlo sul telefono va bene un
qualsiasi gestore file con "hash checker"; su un computer: `sha256sum file.apk` (Linux),
`shasum -a 256 file.apk` (macOS), `certutil -hashfile file.apk SHA256` (Windows).

## Accesso all'account

Nella pagina di Claude scegli **Continua con l'email** e segui le istruzioni.

- **L'accesso con Google di solito non funziona dentro questo tipo di app.** Google blocca
  l'accesso dalle viste web incorporate; è una regola di Google e l'app non la aggira. Usa
  l'opzione email, anche se l'account è nato con Google: usa lo stesso indirizzo.
- Se Claude ti manda un **link** via email: apri l'email sul vecchio telefono e tocca il
  link, poi scegli OldPhoneClaude se Android chiede con quale app aprirlo. Se Android non lo
  chiede, copia il link (tieni premuto → *Copia link*), poi nell'app tieni premuto il
  pulsante del microfono per 3 secondi → inserisci il PIN → **Incolla un link di accesso**.
- Se invece Claude mostra un **codice** da digitare, scrivilo nella pagina.
- Un link aperto su un altro dispositivo fa accedere quel dispositivo, non il telefono.

## Aggiornare l'app

L'app non si aggiorna da sola e non controlla mai gli aggiornamenti.

1. Tieni premuto il microfono 3 secondi → PIN → **Controlla aggiornamenti**, oppure apri la
   pagina Releases in un browser.
2. Scarica l'APK più recente **della stessa edizione** e installalo come sopra. Accesso e
   impostazioni restano.
3. Se il telefono è in **modalità dedicata**, disattivala prima (menu del proprietario),
   aggiorna, poi riattivala.

Se Android dice **"App non installata"** o *"il pacchetto è in conflitto con uno
esistente"*, il nuovo APK è firmato con una chiave diversa da quello installato (vedi
sotto). L'unica via è disinstallare prima il vecchio, perdendo l'accesso.

## Build di debug

Se nel repository non è configurata una chiave di firma, GitHub Actions può produrre solo
APK di **debug**. Le note della release lo dicono nella prima riga e i nomi dei file
finiscono con `-debug`.

- Funzionano normalmente, ma sono più grandi e più lenti delle release.
- **Ogni build è firmata con una chiave usa e getta diversa**, quindi un APK di debug più
  nuovo non si installa sopra uno più vecchio: disinstalla prima, poi accedi di nuovo.
- Per l'uso quotidiano il manutentore dovrebbe configurare la firma: [SIGNING.md](SIGNING.md).

## Disinstallare

- Se l'app è la schermata Home: *Impostazioni → App → App predefinite → App Home*, scegli il
  launcher originale del telefono, poi disinstalla normalmente.
- Se l'app è **proprietario del dispositivo** (modalità dedicata): menu del proprietario →
  **Rinuncia a proprietario del dispositivo**. Fino ad allora Android rifiuta di
  disinstallarla.
