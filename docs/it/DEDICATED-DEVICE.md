# Trasformare il telefono in un dispositivo dedicato a Claude

*English: [docs/en/DEDICATED-DEVICE.md](../en/DEDICATED-DEVICE.md)*

Ci sono due livelli. Comincia da quello base: alla maggior parte delle persone il secondo
non serve mai.

| | Livello base | Livello dispositivo dedicato |
|---|---|---|
| Serve un computer | No | Sì, una volta (adb) |
| Serve il ripristino di fabbrica | No (consigliato) | **Sì**, e nessun account sul telefono |
| Il tasto Home torna a Claude | Sì | Sì |
| Barra di stato e notifiche | Nascoste, riappaiono con un gesto | Bloccate |
| Uscire dall'app | Possibile per chi conosce il gesto | Solo con il PIN del proprietario |
| Rischio | Nessuno | Un errore o un PIN dimenticato possono richiedere il ripristino di fabbrica |

> **Non verificato su dispositivo reale.** Tutto ciò che è scritto in questa pagina deriva
> dalla documentazione di Android ed è stato verificato solo in compilazione. L'autore non
> ha potuto eseguire i comandi "device owner" su un telefono fisico. Provalo su un telefono
> che puoi permetterti di ripristinare e, per favore, segnala com'è andata (modello di issue
> "Device report").

Nessun passo usa il root, sblocca il bootloader, modifica partizioni di sistema, sostituisce
l'animazione di avvio o la schermata di blocco, o disattiva protezioni di Android.

---

## 0. Prepara il telefono (entrambi i livelli)

1. **Salva ciò che vuoi conservare**, poi ripristina: *Impostazioni → Sistema → Opzioni di
   ripristino → Cancella tutti i dati (ripristino di fabbrica)*. Sulle versioni più vecchie:
   *Impostazioni → Backup e ripristino → Ripristino dati di fabbrica*.
2. Completa la configurazione iniziale di Android e collegati al Wi-Fi.
   - **Livello base:** puoi accedere con un account Google, così il Play Store aggiorna il
     motore web.
   - **Livello dedicato:** al passo 2.3 il telefono dovrà essere **senza account**. L'ordine
     più comodo è: accedi a Google, fai qui gli aggiornamenti del passo 3, poi rimuovi
     l'account (*Impostazioni → Account → il tuo account → Rimuovi account*).
3. **Aggiorna ciò che si può aggiornare:**
   - *Impostazioni → Sistema → Aggiornamento di sistema*.
   - Play Store → **Android System WebView** → Aggiorna. Aggiorna anche **Google Chrome**.
   - Per la dettatura: Play Store → **Riconoscimento e sintesi vocale** (o "Speech Services
     by Google") → Aggiorna e, nell'app **Google**, scarica la tua lingua per l'uso offline
     se viene proposto.
4. **Disattiva le app che non servono** (meno memoria occupata, meno notifiche):
   *Impostazioni → App →* tocca un'app → **Disattiva** (o Disinstalla). Candidati tipici:
   social, giochi, store del produttore, notizie, browser doppi. **Non** disattivare:
   Android System WebView, Google Play Services, la tastiera, Impostazioni, Fotocamera,
   File/Documenti e i servizi vocali se vuoi la dettatura. Non serve il root; se un'app non
   si può disattivare, lasciala.
5. *Impostazioni → Display → Sospensione*: va bene qualsiasi valore. L'app tiene acceso lo
   schermo da sola, **solo mentre il telefono è in carica**.
6. Installa OldPhoneClaude ([INSTALL.md](INSTALL.md)) e completa la sua guida, compreso il
   **PIN del proprietario**. Scrivi il PIN da qualche parte.

---

## 1. Livello base (nessun computer)

### 1.1 Schermata Home

Durante la guida dell'app scegli **Usala come schermata Home**. In seguito: menu del
proprietario → **Scegli l'app Home**. Da quel momento il tasto Home, e il riavvio del
telefono, portano a Claude.

Per annullare: *Impostazioni → App → App predefinite → App Home* → scegli il launcher
originale.

### 1.2 Gesto dell'assistente (facoltativo)

Menu del proprietario → **Impostazioni dell'app assistente** → *App assistente digitale* →
OldPhoneClaude. Il gesto dell'assistente del telefono (pressione lunga su Home; su alcuni
telefoni uno scorrimento dall'angolo o la pressione lunga del tasto di accensione) avvia
l'ascolto.

Volutamente **non esiste un "Ehi Claude"**: il telefono non ascolta mai a schermo spento o
in background. Usa il pulsante grande o il gesto di sistema.

### 1.3 Schermo sempre acceso in carica

Niente da fare: menu del proprietario → *Schermo* contiene **Tieni lo schermo acceso in
carica** (attivo all'inizio) e la **schermata di riposo scura**. Dopo 1 minuto senza tocchi
lo schermo si attenua; dopo 3 minuti compare una schermata nera con un piccolo orologio che
si sposta leggermente ogni minuto per proteggere gli schermi OLED. Un tocco la riattiva.

### 1.4 Fissaggio schermo

Il fissaggio schermo è la funzione di Android per "restare in questa app".

1. Attivalo in Android: *Impostazioni → Sicurezza → Fissaggio schermo* (Android 9+:
   *Sicurezza → Avanzate → Blocco app su schermo*; Samsung: *Dati biometrici e sicurezza →
   Altre impostazioni di sicurezza → Blocca finestre*). Attiva anche **Richiedi PIN/sequenza
   prima di sbloccare** se vuoi che per uscire serva il blocco schermo del telefono.
2. Nell'app: tieni premuto il microfono 3 secondi → PIN → **Fissa lo schermo (blocco
   base)**. Android chiede conferma.
3. **Per sbloccare:** tieni premuti insieme **Indietro** e **Panoramica**. Con la
   navigazione a gesti: scorri dal basso verso l'alto e tieni premuto. Oppure menu del
   proprietario → **Sblocca lo schermo fissato**.

Limiti del livello base: il fissaggio termina al riavvio del telefono, e chi conosce il
gesto di sblocco può uscire, a meno che tu abbia attivato la richiesta di sblocco al punto 1.

---

## 2. Livello dispositivo dedicato (computer con adb)

Qui l'app diventa **proprietario del dispositivo** ("device owner"), un ruolo che Android
riserva a una sola app di gestione. Solo un device owner può usare la *lock task mode* senza
conferma: niente barra di stato, niente notifiche, niente Home o Recenti, nessuna uscita se
non attraverso l'app.

### Da leggere prima

- **Funziona solo su un telefono senza account e senza altri utenti.** Altrimenti Android
  rifiuta. Per questo il telefono deve essere appena ripristinato.
- **Se dimentichi il PIN del proprietario, o l'app smette di funzionare mentre la modalità
  dedicata è attiva, l'unica via d'uscita può essere il ripristino di fabbrica dal menu di
  recovery,** che cancella il telefono.
- Finché l'app è device owner **non può essere disinstallata** e il telefono non può avere
  un altro device owner.
- Lascia attivo il debug USB finché non sei sicuro che tutto funzioni: permette di
  installare dal computer una versione corretta dell'app.

### 2.1 Installa adb sul computer

Scarica **SDK Platform-Tools** da `https://developer.android.com/tools/releases/platform-tools`
(Windows, Linux o macOS), estrai lo zip e apri un terminale in quella cartella. Su Linux puoi
anche installare il pacchetto `adb` della tua distribuzione.

### 2.2 Attiva il debug USB sul telefono

1. *Impostazioni → Info sul telefono* → tocca sette volte **Numero build**.
2. *Impostazioni → Sistema → Opzioni sviluppatore* → attiva **Debug USB**.
3. Collega il telefono con un cavo USB **dati**. Sul telefono accetta *Consentire debug USB*.
4. Sul computer:

```bash
adb devices
```

Il telefono deve comparire come `device` (non `unauthorized`).

### 2.3 Rimuovi tutti gli account

*Impostazioni → Account* deve essere vuoto (Google, Samsung, Xiaomi, email... tutti). Vanno
rimossi anche altri utenti o un profilo di lavoro. Controllo dal computer:

```bash
adb shell dumpsys account | grep -c "Account {"
```

Deve stampare `0`.

### 2.4 Rendi l'app device owner

OldPhoneClaude deve essere già installata. Poi:

```bash
adb shell dpm set-device-owner io.github.forkyfor.oldphone/.AdminReceiver
```

Risposta attesa:

```
Success: Device owner set to package io.github.forkyfor.oldphone/.AdminReceiver
```

Errori comuni:

| Il messaggio contiene | Significato | Rimedio |
|---|---|---|
| `there are already some accounts on the device` | C'è ancora un account | Rimuovilo (2.3). Alcuni account del produttore spariscono solo con un ripristino di fabbrica. |
| `there are already several users on the device` | Esiste un secondo utente, un ospite o un profilo di lavoro | Rimuovilo in *Impostazioni → Sistema → Utenti multipli* |
| `device is already provisioned` / `already set` | Esiste già un device owner o un profilo di gestione | Ripristino di fabbrica, salta gli account, riprova |
| `Unknown admin` | L'app non è installata, o c'è un errore di battitura | Installa l'app; copia il comando esattamente |

### 2.5 Attiva la modalità dedicata

Nell'app: tieni premuto il microfono 3 secondi → PIN → *Home e blocco* → **Modalità
dedicata** → **Torna a Claude**. Da questo momento:

- l'app è la schermata Home per criterio di sistema e parte all'accensione;
- barra di stato, notifiche, Home e Recenti sono disattivati (su Android 9 e successivi
  resta solo il menu di spegnimento, così il telefono si può ancora spegnere);
- Impostazioni di Android, fotocamera e selettore file si possono ancora aprire, ma solo
  dall'app (le Impostazioni solo dal menu del proprietario, protetto dal PIN);
- i link verso altri siti non vengono aperti affatto.

### 2.6 Come annullare

Dal meno al più drastico:

1. **Pausa:** menu del proprietario → disattiva **Modalità dedicata**. Il telefono torna a
   comportarsi normalmente; l'app resta device owner e puoi riattivare la modalità quando
   vuoi.
2. **Restituire il telefono:** menu del proprietario → **Rinuncia a proprietario del
   dispositivo**. L'app torna a essere una normale app disinstallabile. Per riavere la
   modalità dedicata serviranno di nuovo ripristino di fabbrica e adb.
3. **Se l'app non parte o hai perso il PIN, con il debug USB ancora attivo:** installa dal
   computer una versione funzionante sopra quella presente (`adb install -r
   OldPhoneClaude-….apk`; deve essere firmata con la stessa chiave). `adb uninstall` e
   `dpm remove-active-admin` **non** funzionano su un device owner installato normalmente.
4. **Ultima risorsa: ripristino di fabbrica dal recovery.** Spegni il telefono, poi tieni
   premuta la combinazione di tasti del tuo modello (spesso **Accensione + Volume su**, sui
   Samsung anche Home/Bixby) finché compare il menu di recovery, e scegli *Wipe data /
   factory reset*. Cancella tutto il contenuto del telefono. Poiché sul telefono non c'è un
   account Google, dopo non ne verrà chiesto uno (nessun blocco "protezione ripristino dati
   di fabbrica").

### 2.7 Aggiornare l'app in modalità dedicata

Disattiva la modalità dedicata (2.6, punto 1), installa il nuovo APK
([INSTALL.md](INSTALL.md)), riattivala. Oppure dal computer: `adb install -r
OldPhoneClaude-….apk`.

---

## Convivere con un vecchio telefono sempre in carica

Leggi [Batteria e sicurezza](../../README.it.md#batteria-e-sicurezza). In breve: una batteria
vecchia tenuta al 100% tutto il giorno invecchia prima e può gonfiarsi. Controllala
regolarmente, tieni il telefono su una superficie non infiammabile e lontano dal sole, e non
lasciarlo in carica incustodito per giorni.
