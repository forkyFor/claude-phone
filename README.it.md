# OldPhoneClaude

**Trasforma un vecchio telefono Android in un dispositivo dedicato e sempre acceso per Claude.**

*English: [README.md](README.md)*

> **Progetto indipendente.** OldPhoneClaude non è affiliato, approvato né supportato da
> Anthropic. "Claude" è un marchio di Anthropic. Questa app è un browser dedicato al sito
> ufficiale **https://claude.ai**: l'accesso avviene lì, con il tuo account. Il nome è
> provvisorio e può cambiare.

Accendi il telefono, parli, usi Claude. Niente barra degli indirizzi, niente schede, niente
home di Android: il telefono *è* il dispositivo per Claude sulla scrivania, in cucina o sul
comodino.

## Cosa fa

- Mostra il **sito ufficiale claude.ai** a schermo intero (chat, progetti, tutto ciò che il
  tuo account ha in un browser).
- Una sola barra nativa discreta con quattro azioni: **Nuova chat**, **Progetti**, un grande
  **Microfono**, **Fotocamera**, più un pulsante **App** che apre le applicazioni installate
  e le impostazioni del telefono (disattivabile dal menu del proprietario). Si nasconde
  mentre scrivi o leggi.
- Può essere la **schermata Home** e l'**app assistente** del telefono: il tasto Home e il
  gesto dell'assistente portano sempre a Claude; riparte dopo un riavvio.
- **Dettatura** con il riconoscimento vocale del telefono, tre comandi a voce ("nuova chat",
  "apri progetti", "indietro") e **lettura ad alta voce** opzionale con un pulsante Ferma
  ben visibile.
- **Allega** foto dalla fotocamera, immagini e file; **riceve** testo, link, immagini e PDF
  dal menu Condividi di Android in una nuova chat.
- **Primo avvio guidato** in linguaggio semplice (italiano e inglese) e **schermate native**
  che spiegano cosa non va (rete assente, motore web troppo vecchio, permesso negato) al
  posto di una pagina bianca.
- **Comportamento da dispositivo sempre acceso**: schermo acceso solo in carica,
  attenuazione, schermata di riposo scura con orologio mobile per gli schermi OLED.
- Due livelli di blocco: il **fissaggio schermo** di Android (senza computer) oppure una
  **modalità dedicata** completa (device owner + lock task) da impostare una volta con adb.
- Un **menu del proprietario** nascosto (tieni premuto il microfono 3 s + un PIN scelto da
  te): Wi-Fi, volume, luminosità, motore web, aggiornamenti, uscita dall'account, uscita
  dalla modalità dedicata.

## Cosa NON fa

- **Nessuna API di Anthropic e nessuna chiave API.** Non è un client alternativo.
- **Non legge, salva né invia cookie, token o password**, non chiama endpoint privati, non
  fa scraping delle conversazioni e non aggira blocchi, controlli anti-bot, limiti o
  restrizioni di accesso.
- **Non sostituisce, nasconde né ristilizza** l'interfaccia di Claude. Alla pagina non viene
  applicato alcun ritocco di stile.
- **Nessun server dell'autore, nessuna analitica, nessuna pubblicità.** L'unico traffico di
  rete è quello del sito che vedi. (Il servizio di riconoscimento vocale, se usi il pulsante
  del microfono, è quello del tuo telefono, spesso di Google.)
- **Niente root**, niente partizioni di sistema, niente sostituzione dell'animazione di
  avvio o della schermata di blocco, nessuna protezione di Android disattivata.
- Nessun "Ehi Claude": il telefono non ascolta mai a schermo spento.

## Requisiti

- Un telefono con **Android 5.0 o successivo** per installare l'app. Che il sito funzioni
  davvero dipende dal **motore web**: vedi [Compatibilità](#compatibilità).
- **Un tuo account Claude** (gratuito o a pagamento). Nessuna chiave API.
- Wi-Fi. Un caricatore e una batteria in buone condizioni: leggi
  [Batteria e sicurezza](#batteria-e-sicurezza).
- Nessun computer, nessun Mac, nessun account sviluppatore. Un computer con `adb` serve solo
  per la modalità dedicata, che è facoltativa.

## Installazione

Guida passo passo per chi non è tecnico, comprese le "origini sconosciute" e gli
aggiornamenti: **[docs/it/INSTALL.md](docs/it/INSTALL.md)**.

In breve: dal telefono apri la pagina
[Releases](https://github.com/forkyFor/claude-phone/releases), scarica l'APK `lite`,
consenti l'installazione, aprilo e segui la guida.

| Edizione | Android | Motore web | Dimensione APK |
|---|---|---|---|
| **lite** | 5.0+ | l'Android System WebView del telefono | circa 0,1 MB (release firmata), circa 1 MB (debug) |
| **gecko** | 8.0+ | WebView **oppure** GeckoView incluso (il motore di Firefox), selezionabile dal menu del proprietario | grande: 160–200 MB come build di debug |

## Cosa funziona con cosa

| Funzione | Solo APK | Serve adb (una volta) | Non possibile |
|---|:-:|:-:|:-:|
| Sito ufficiale a schermo intero, accesso persistente, tasto indietro, ricarica | ✔ | | |
| Barra nativa, dettatura, lettura, fotocamera, allegati, condivisione in ingresso | ✔ | | |
| Schermata Home: il tasto Home torna a Claude, riparte dopo il riavvio | ✔ | | |
| Il gesto dell'assistente avvia l'ascolto | ✔ (scegli l'app nelle impostazioni di Android) | | |
| Schermo intero immersivo (le barre tornano con un gesto dal bordo) | ✔ | | |
| Schermo acceso in carica, attenuazione, schermata di riposo | ✔ | | |
| Fissaggio schermo (blocco base) | ✔ (Android chiede conferma) | | |
| Avvio all'accensione senza essere l'app Home, Android 10+ | | | ✘ Android lo vieta |
| Modalità dedicata: niente barra di stato, notifiche, Home/Recenti; uscita solo con PIN | | ✔ `dpm set-device-owner` su telefono ripristinato e senza account | |
| Nascondere il menu di spegnimento / impedire lo spegnimento | | | ✘ non tentato (sicurezza) |
| "Ehi Claude" a schermo spento | | | ✘ non è un obiettivo |
| Accesso con Google dentro il WebView di sistema | | | ✘ di solito bloccato da Google; non aggirato |
| Passare all'app un accesso fatto in Chrome/Custom Tabs | | | ✘ richiederebbe copiare i cookie, cosa che questa app rifiuta di fare |
| Sostituire animazione di avvio o schermata di blocco, tutto ciò che richiede il root | | | ✘ fuori dallo scopo |

Entrambi i livelli di blocco, come annullarli e cosa fare se qualcosa va storto:
**[docs/it/DEDICATED-DEVICE.md](docs/it/DEDICATED-DEVICE.md)**.

## Compatibilità

La tabella completa, come è stata misurata e come aggiungere il tuo telefono con una pull
request: **[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)**.

**Stato al 16 novembre 2026, onestamente:**

| | |
|---|---|
| Build in CI, 12 test unitari, lint | ✔ verificato (GitHub Actions verde) |
| Si installa e parte su Android 5.0 → 15 | ✔ verificato **solo su emulatore** |
| Guida iniziale, PIN, menu del proprietario, schermate di errore native | ✔ verificato **solo su emulatore** (tocchi automatici, non a occhio) |
| La pagina di accesso ufficiale viene disegnata | ✔ su emulatore con **GeckoView 160 (Android 8.0+)**; WebView 83 su emulatore, ma **un telefono reale con WebView non aggiornato ha mostrato una pagina rotta**, quindi ora l'app richiede WebView 111+ · ✘ WebView ≤ 74 · ✘ Android ≤ 7.0 (certificato non attendibile) |
| Accesso, chat, voce, fotocamera, file, condivisione, download | **non verificato su dispositivo reale** (le prove non hanno account, microfono né fotocamera) |
| Ruolo Home, assistente, fissaggio schermo, modalità dedicata, schermata di riposo | **non verificato su dispositivo reale** |

Minimo realistico oggi: **Android 8.0+** con Android System WebView aggiornato, oppure
l'edizione gecko. L'app si installa su Android 5–7 e spiega il problema con una schermata
nativa, ma lì il sito molto probabilmente non può caricarsi.

## Accesso all'account

Nella pagina di Claude usa **Continua con l'email**.

- L'**accesso con Google** di norma viene rifiutato da Google dentro le viste web
  incorporate ("Questo browser o questa app potrebbero non essere sicuri"). L'app non si
  camuffa per aggirarlo. Se il tuo account è nato con Google, accedi via email con lo stesso
  indirizzo.
- Un accesso fatto nel **browser di sistema o in una Custom Tab non può essere passato
  all'app**: browser e viste web hanno archivi di cookie separati, e spostare una sessione
  dall'uno all'altra significherebbe estrarre i cookie, cosa che questo progetto non fa mai.
  Per questo l'app non offre un pulsante "accedi con il browser"; i link ad altri siti si
  aprono semplicemente nel browser di sistema.
- Con l'edizione **gecko** e il motore GeckoView selezionato, la pagina gira in un motore di
  browser completo con il suo normale user agent. Se Google accetti l'accesso lì **non è
  stato verificato**.
- **SSO aziendale**: aggiungi il sito di accesso della tua organizzazione dal menu del
  proprietario → *Siti di accesso aggiuntivi*.
- Se Claude ti invia per email un **link** di accesso, aprilo sul telefono, oppure copialo e
  usa menu del proprietario → **Incolla un link di accesso** (sono accettati solo link
  `https://claude.ai/…`).

## Come si usa

| Vuoi | Fai così |
|---|---|
| Parlare | Tocca il microfono grande, parla, tocca il pulsante di invio della pagina. Di' solo "nuova chat", "apri progetti" o "indietro" per navigare. |
| Usare la modalità vocale di Claude | Usa il pulsante voce dentro la pagina; l'app passa il microfono alla pagina. |
| Allegare una foto | Tocca **Fotocamera**, scatta, poi tocca il pulsante allega (+) **nella pagina**: la foto viene aggiunta senza altre domande. Oppure tocca prima il pulsante allega della pagina e scegli Fotocamera / Foto / File. |
| Inviare qualcosa da un'altra app | Condividi → OldPhoneClaude. Il testo apre una nuova chat già compilata; i file restano in attesa finché tocchi il pulsante allega della pagina. |
| Ricaricare | Trascina verso il basso dal bordo superiore della pagina finché compare "Rilascia per ricaricare", oppure menu del proprietario → Ricarica. |
| Aprire il menu del proprietario | Tieni premuto il microfono per 3 secondi e inserisci il PIN. Con TalkBack: il pulsante del microfono ha l'azione "Menu del proprietario". |
| Riattivare dalla schermata di riposo | Toccala, oppure usa il gesto dell'assistente. |

L'indirizzo claude.ai/new accetta un parametro `q` che precompila il campo del messaggio
([articolo di aiuto di Anthropic](https://support.claude.com/en/articles/14729294-open-claude-desktop-with-a-link));
la condivisione di testo usa quel link documentato.

## Fragili per natura: i tre "strumenti di pagina"

L'app esegue esattamente tre piccole funzioni dentro la pagina, su richiesta
([`app/src/main/assets/pagetools/lib.js`](app/src/main/assets/pagetools/lib.js)):

1. **insertText**: scrive il testo dettato nel campo del messaggio.
2. **lastReply**: restituisce il testo da leggere ad alta voce (prima la tua selezione,
   altrimenti l'ultima risposta che riesce a trovare).
3. **probe**: conta quanta parte della pagina è stata disegnata, per riconoscere una pagina
   bianca.

Dipendono da come la pagina è costruita oggi e **possono rompersi ogni volta che il sito
cambia**. Non nascondono né modificano nulla e ciò che leggono non esce dal telefono.
Dettatura e lettura si possono disattivare dal menu del proprietario; quando la dettatura
non trova il campo, il testo viene copiato per poterlo incollare.

## Risoluzione dei problemi

| Problema | Cosa provare |
|---|---|
| "Il motore web è troppo vecchio" / "La pagina non è comparsa" | Play Store → aggiorna **Android System WebView** e **Chrome**. Se non viene proposto alcun aggiornamento, quella versione di Android è arrivata al capolinea per questo motore: su Android 8+ installa l'edizione **gecko** e scegli GeckoView dal menu del proprietario. |
| L'accesso con Google mostra un errore | È previsto. Torna indietro e usa **Continua con l'email**. |
| "Connessione sicura non riuscita" | Imposta **data e ora** corrette; aggiorna il motore web. |
| Compare di continuo "Verifica che sei umano" | È il controllo anti-bot del sito. Completalo; se non passa mai, probabilmente il motore è troppo vecchio. L'app non lo aggira. |
| Il microfono dice che la dettatura non è disponibile | Installa/attiva un servizio di riconoscimento vocale (per esempio "Riconoscimento e sintesi vocale" di Google) e consenti il microfono. |
| Il testo dettato non compare nel campo | L'aiuto non ha trovato il campo (il sito è cambiato). Il testo è negli appunti: tieni premuto sul campo → Incolla. Per favore apri una issue. |
| "App non installata" durante l'aggiornamento | Il nuovo APK ha una firma diversa (build di debug). Disinstalla, reinstalla, accedi di nuovo. Vedi [INSTALL](docs/it/INSTALL.md#build-di-debug). |
| Foto o file non vengono allegati | Dopo lo scatto tocca il pulsante allega nella pagina. Da Condividi non si accettano file oltre 30 MB. |
| Il telefono è lento o la pagina si ricarica da sola | I telefoni vecchi hanno poca memoria: disattiva le app inutili, riavvia, attiva *Riduci le animazioni*. |
| PIN del proprietario dimenticato | *Impostazioni di Android → App → OldPhoneClaude → Spazio di archiviazione → Cancella dati* azzera l'app (e ti disconnette). In modalità dedicata non è raggiungibile: vedi [le vie d'uscita](docs/it/DEDICATED-DEVICE.md#26-come-annullare). |
| Bloccato in modalità dedicata | [docs/it/DEDICATED-DEVICE.md](docs/it/DEDICATED-DEVICE.md#26-come-annullare) |

## Limiti noti

- **Non provato dall'autore su un telefono reale.** Vedi la tabella di stato qui sopra e
  [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **Download:** con il motore WebView i normali download https vengono passati al browser di
  sistema (che può non essere connesso all'account) e i file generati dentro la pagina
  (download `blob:`, come gli artefatti esportati) **non sono supportati**. Con GeckoView i
  download vengono salvati nella cartella Download.
- Le **notifiche** del sito non sono supportate (il WebView di sistema non ha notifiche web;
  in GeckoView vengono rifiutate).
- **Video a schermo intero** e richieste di **posizione** vengono rifiutati.
- La barra si nasconde allo scorrimento e ricompare dopo qualche secondo di quiete; le
  pagine che non scorrono non cambiano.
- Il fissaggio schermo termina al riavvio (comportamento di Android). La modalità dedicata
  no.
- La schermata di riposo non può sapere se è in uso la modalità vocale della pagina: tocca
  lo schermo per tenerlo sveglio nelle sessioni vocali lunghe, o disattivala.
- Telefono vecchio significa **Android vecchio e senza correzioni di sicurezza**. Vedi
  [SECURITY.md](SECURITY.md).

## Batteria e sicurezza

I telefoni vecchi lasciati sempre in carica vanno trattati con rispetto.

- **Una batteria gonfia, danneggiata o molto calda è pericolosa** (incendio, fumi tossici).
  Segnali: la scocca posteriore si gonfia, lo schermo si solleva o mostra aloni di
  pressione, il telefono dondola su un tavolo piano, scalda anche da fermo. **Smetti di
  usarlo, scollegalo, non premerlo né forarlo,** e portalo a un centro di raccolta RAEE.
- Se il telefono ha la **batteria rimovibile** e si accende lo stesso dal caricatore senza
  batteria, usarlo così elimina il rischio. Molti telefoni non partono senza batteria: è
  normale.
- Se le impostazioni del telefono offrono un **limite di carica** ("Proteggi batteria",
  "Limita all'80/85%", "Cura batteria"), attivalo. Non usare nulla che richieda il root.
- In alternativa, una semplice **presa con timer** che alimenta il caricatore poche ore al
  giorno evita che la batteria resti sempre al 100%.
- Usa il caricatore originale o uno certificato, un cavo integro, una **superficie rigida e
  non infiammabile**, lontano da sole, termosifoni, letti e tende. Togli le custodie spesse.
- **Non lasciarlo in carica incustodito per giorni** e controlla la batteria ogni poche
  settimane.
- Luminosità bassa e schermata di riposo scura riducono calore e burn-in degli OLED.

## Privacy

- L'app non ha server e non invia nulla a nessuno. Chiede solo i permessi che vedi:
  internet, microfono, fotocamera, avvio all'accensione.
- La sessione resta nell'archivio del motore web sul telefono, come in un browser, ed è
  esclusa dai backup di Android.
- Foto e file condivisi in attesa di essere allegati stanno nella cache privata dell'app e
  vengono cancellati dopo un giorno.
- Il PIN del proprietario è salvato solo come hash con sale.
- Ciò che fai su claude.ai è regolato dai termini e dall'informativa privacy di Anthropic.

## Compilazione e release

Non servono un Mac né una configurazione locale: **compila tutto GitHub Actions.**

| Workflow | Quando | Cosa |
|---|---|---|
| [Build](.github/workflows/build.yml) | a ogni push e pull request | test unitari, lint, tutti gli APK come artifact, SHA-256 nel riepilogo |
| [Release](.github/workflows/release.yml) | a ogni tag `v*` | GitHub Release con gli APK, `SHA256SUMS.txt` e le impronte nelle note |
| [Compatibility probe](.github/workflows/emulator.yml) | a mano | avvia emulatori Android e registra se l'app parte e se il sito viene disegnato |

La chiave di firma sta **solo nei Secrets di GitHub**, mai nel repository. Senza il secret la
CI produce APK di **debug** e lo dichiara. Come creare la chiave senza un Mac:
**[docs/it/SIGNING.md](docs/it/SIGNING.md)**.

In locale (facoltativo): JDK 21 + Android SDK, poi `./gradlew :app:assembleLiteDebug`.

F-Droid: valutato, nulla è stato inviato. Vedi [docs/it/FDROID.md](docs/it/FDROID.md).

## Struttura del progetto

```
app/src/main/java/.../oldphone/
  MainActivity.kt        la schermata del dispositivo: motore, barra, voce, allegati, errori, riposo
  SetupActivity.kt       primo avvio guidato
  OwnerActivity.kt       menu del proprietario      PinPrompt.kt / Pin.kt   PIN
  Voice.kt               dettatura, comandi, lettura
  Kiosk.kt               device owner / lock task   App.kt   riavvio dopo un blocco, avvio
  engine/UrlPolicy.kt    quali link restano dentro (con test unitari)
  engine/SystemWebViewEngine.kt   Android System WebView
app/src/gecko/           motore GeckoView (solo edizione gecko)
app/src/main/assets/pagetools/lib.js   l'unico codice eseguito nella pagina
docs/                    guide (en, it) e tabella di compatibilità
.github/workflows/       build, release, sonda su emulatori
```

## Licenza

[Apache License 2.0](LICENSE). Preferita alla MIT perché è altrettanto permissiva ma aggiunge
una **concessione esplicita sui brevetti** e dichiara chiaramente di **non concedere alcun
diritto sui marchi**, cosa importante per un progetto il cui nome cita il prodotto di
qualcun altro. È inoltre compatibile con la MPL-2.0 di GeckoView. Vedi [NOTICE](NOTICE).

## Contribuire

Le segnalazioni sui dispositivi sono il contributo più prezioso:
[CONTRIBUTING.md](CONTRIBUTING.md) · [Codice di condotta](CODE_OF_CONDUCT.md) ·
[Sicurezza](SECURITY.md).
