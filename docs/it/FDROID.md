# Pubblicazione su F-Droid: valutazione

*English: [docs/en/FDROID.md](../en/FDROID.md)*

**Nulla è stato inviato a F-Droid.** Questa pagina è una valutazione e una lista di controllo
per chi mantiene il progetto. Riflette la nostra lettura delle regole di inclusione di
F-Droid; decidono i revisori di F-Droid.

## Edizione lite: probabilmente ammissibile

| Requisito | Stato |
|---|---|
| Licenza libera | Apache-2.0 |
| Nessuna libreria proprietaria | Nessuna. L'edizione lite **non ha dipendenze** oltre alla libreria standard di Kotlin; niente Google Play Services, Firebase, analitica o pubblicità. |
| Si compila dai sorgenti con strumenti liberi | Gradle + Android Gradle Plugin, sorgenti e plugin da Maven Central / Google Maven. |
| Nessun blocco di metadati sulle dipendenze | È impostato `dependenciesInfo { includeInApk = false }`. |
| Metadati per lo store | `fastlane/metadata/android/{en-US,it-IT}` (titolo, descrizioni, changelog). Mancano ancora gli screenshot. |
| Tag di versione | Tag `vX.Y.Z` uguali a `versionName`; `versionCode` deve crescere a ogni release. |

Punti che un revisore solleverà:

- **Anti-feature "NonFreeNet".** L'app è utile solo con claude.ai, un servizio di rete
  proprietario che richiede un account. F-Droid accetta queste app ma le etichetta.
- **Nome e marchio.** "Claude" è un marchio di Anthropic. Nome, descrizione e icona devono
  chiarire che il progetto non è ufficiale; F-Droid (o Anthropic) potrebbero comunque
  chiedere un nome diverso. Il nome è definito in un solo punto: `app_name` in
  `app/src/main/res/values/strings.xml`.
- **WebView di sistema.** L'edizione lite usa Android System WebView, che sulla maggior
  parte dei telefoni è una build proprietaria di Google. È normale per le app su F-Droid.
- **Riconoscimento vocale:** usa il servizio presente sul telefono (spesso di Google). È
  facoltativo; merita una riga nella descrizione.

## Edizione gecko: probabilmente non ammissibile così com'è

L'edizione gecko scarica **binari precompilati di GeckoView** da `maven.mozilla.org`.
GeckoView è software libero (MPL-2.0), ma F-Droid di norma richiede che i binari arrivino dai
repository che considera affidabili o siano compilati dai sorgenti, e compilare Gecko dai
sorgenti è un lavoro molto grande. Non abbiamo verificato come F-Droid tratti oggi quel
repository. Il piano realistico è proporre solo l'edizione lite (`gradle: [lite]`) e
continuare a distribuire la gecko con le GitHub Releases.

## Bozza dei metadati e lista di controllo

La bozza YAML per `fdroiddata` e la lista di controllo sono nella
[versione inglese](../en/FDROID.md#draft-metadata-for-the-fdroiddata-repository): sono
identiche e vanno comunque scritte in inglese per F-Droid.
