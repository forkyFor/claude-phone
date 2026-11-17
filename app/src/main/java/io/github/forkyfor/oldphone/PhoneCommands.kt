package io.github.forkyfor.oldphone

import java.text.Normalizer
import java.util.Locale

/** A spoken request about the phone itself, recognised locally. Nothing here leaves the device. */
sealed class PhoneCommand {
    data class Call(val who: String) : PhoneCommand()
    /** "Call the number": the number is taken from Claude's last reply, then confirmed. */
    object CallFromReply : PhoneCommand()
    /** [channel] null = the app chosen in the owner menu (WhatsApp if installed, else SMS). */
    data class Message(val who: String, val body: String?, val channel: Channel? = null) : PhoneCommand()
    object VolumeUp : PhoneCommand()
    object VolumeDown : PhoneCommand()
    object Mute : PhoneCommand()
    data class Torch(val on: Boolean) : PhoneCommand()
    object Time : PhoneCommand()
    object Battery : PhoneCommand()
    object Settings : PhoneCommand()
    object Wifi : PhoneCommand()
    data class OpenApp(val name: String) : PhoneCommand()
    object Help : PhoneCommand()
    object MeetingStart : PhoneCommand()
    object MeetingStop : PhoneCommand()
    object MeetingsOpen : PhoneCommand()
}

enum class Answer { YES, NO }

enum class Channel { WHATSAPP, SMS }

/**
 * Parser for the phone commands, in English and Italian. Pure Kotlin so it is unit tested.
 *
 * Commands must be short: anything longer than a few words after the verb is treated as a
 * message for Claude and typed into the chat, so a sentence like "chiama gli amici di mio
 * fratello per organizzare..." is never mistaken for a phone call.
 */
object PhoneCommands {
    private const val MAX_NAME_WORDS = 5
    private const val MAX_APP_WORDS = 4

    private val fixed: Map<String, PhoneCommand> = HashMap<String, PhoneCommand>().apply {
        fun add(cmd: PhoneCommand, vararg phrases: String) = phrases.forEach { put(it, cmd) }
        add(
            PhoneCommand.CallFromReply,
            "chiama il numero", "chiama questo numero", "chiama quel numero", "chiama il numero nella risposta",
            "chiama il numero della risposta", "chiamalo", "chiamala", "chiama quel posto",
            "call the number", "call this number", "call that number", "call the number in the reply", "call it", "call them",
        )
        add(
            PhoneCommand.VolumeUp,
            "alza il volume", "alza volume", "volume su", "più volume", "piu volume", "aumenta il volume", "alza l'audio",
            "volume up", "louder", "turn up the volume", "turn the volume up",
        )
        add(
            PhoneCommand.VolumeDown,
            "abbassa il volume", "abbassa volume", "volume giù", "volume giu", "meno volume", "diminuisci il volume",
            "abbassa l'audio", "volume down", "quieter", "turn down the volume", "turn the volume down",
        )
        add(PhoneCommand.Mute, "silenzia", "muto", "togli l'audio", "togli il volume", "mute", "silence", "mute the sound")
        add(
            PhoneCommand.Torch(true),
            "accendi la torcia", "accendi torcia", "torcia", "accendi la luce della torcia",
            "turn on the flashlight", "flashlight on", "flashlight", "torch on", "turn on the torch",
        )
        add(
            PhoneCommand.Torch(false),
            "spegni la torcia", "spegni torcia", "turn off the flashlight", "flashlight off", "torch off", "turn off the torch",
        )
        add(
            PhoneCommand.Time,
            "che ore sono", "che ora è", "che ora e", "dimmi l'ora", "che ore sono adesso",
            "what time is it", "what's the time", "tell me the time",
        )
        add(
            PhoneCommand.Battery,
            "batteria", "quanta batteria", "livello batteria", "livello della batteria", "quanta batteria ho",
            "quanta batteria c'è", "quanta carica ho", "battery", "battery level", "how much battery", "how much battery is left",
        )
        add(
            PhoneCommand.Settings,
            "apri impostazioni", "apri le impostazioni", "impostazioni", "impostazioni del telefono",
            "open settings", "settings", "phone settings",
        )
        add(
            PhoneCommand.Wifi,
            "apri wifi", "apri il wifi", "apri wi-fi", "apri il wi-fi", "wifi", "wi-fi", "impostazioni wifi",
            "impostazioni wi-fi", "open wifi", "open wi-fi", "wifi settings", "wi-fi settings",
        )
        add(
            PhoneCommand.MeetingStart,
            "registra la riunione", "registra riunione", "registra una riunione", "inizia la registrazione",
            "avvia la registrazione", "nuova riunione", "record the meeting", "record a meeting", "record meeting",
            "start recording", "new meeting",
        )
        add(
            PhoneCommand.MeetingStop,
            "ferma la registrazione", "termina la registrazione", "stop alla registrazione", "fine riunione",
            "fine della riunione", "stop recording", "stop the recording", "end the meeting",
        )
        add(PhoneCommand.MeetingsOpen, "apri le riunioni", "apri riunioni", "riunioni", "open meetings", "show meetings", "meetings")
        add(
            PhoneCommand.Help,
            "comandi vocali", "quali comandi", "cosa posso dire", "cosa puoi fare", "aiuto comandi",
            "voice commands", "what can i say", "what can you do", "help with commands",
        )
    }

    private val callRegex = Regex("^(?:chiama|telefona a|telefona|fai una telefonata a|fai una chiamata a|call|phone|ring|dial) (.+)$")
    private const val SAYING_IT = "(?: (?:dicendo|che dice|con scritto|scrivendo) (.+))?$"
    private const val SAYING_EN = "(?: (?:saying|that says) (.+))?$"
    private const val TO_IT = "(?:a|al|alla|allo|ad)"
    private val messageRegexes: List<Pair<Regex, Channel?>> = listOf(
        Regex("^(?:manda|invia|scrivi) (?:un )?(?:whatsapp|messaggio whatsapp|messaggio su whatsapp|messaggio con whatsapp) $TO_IT (.+?)$SAYING_IT") to Channel.WHATSAPP,
        Regex("^(?:manda|invia|scrivi) (?:un )?sms $TO_IT (.+?)$SAYING_IT") to Channel.SMS,
        Regex("^(?:manda|invia|scrivi) (?:un )?messaggio $TO_IT (.+?)$SAYING_IT") to null,
        Regex("^(?:send|write) (?:a )?(?:whatsapp|whatsapp message) to (.+?)$SAYING_EN") to Channel.WHATSAPP,
        Regex("^whatsapp (.+?) saying (.+)$") to Channel.WHATSAPP,
        Regex("^(?:send|write) (?:an )?sms to (.+?)$SAYING_EN") to Channel.SMS,
        Regex("^(?:send|write) (?:a )?(?:message|text) to (.+?)$SAYING_EN") to null,
        Regex("^text (.+?)(?: saying (.+))?$") to null,
    )
    private val openRegex = Regex("^(?:apri|avvia|lancia|open|launch|start) (?:l'app |l app |la app |app |the app )?(.+)$")
    private val leadingArticle = Regex("^(?:il|lo|la|l'|i|gli|le|al|alla|allo|a|mia|mio|the|my) ")

    private val yes = setOf(
        "sì", "si", "certo", "conferma", "confermo", "chiama", "procedi", "ok", "okay", "va bene", "sì chiama", "si chiama",
        "yes", "yeah", "yep", "confirm", "go ahead", "do it", "call",
    )
    private val no = setOf("no", "annulla", "lascia stare", "non chiamare", "stop", "cancel", "never mind", "nope", "don't call")

    fun parse(utterance: String): PhoneCommand? {
        val n = VoiceCommands.normalize(utterance)
        if (n.isEmpty()) return null
        fixed[n]?.let { return it }
        for ((r, channel) in messageRegexes) {
            val m = r.find(n) ?: continue
            val who = target(m.groupValues[1]) ?: return null
            val body = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            return PhoneCommand.Message(who, body, channel)
        }
        callRegex.find(n)?.let { m -> return target(m.groupValues[1])?.let { PhoneCommand.Call(it) } }
        openRegex.find(n)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotEmpty() && name.split(' ').size <= MAX_APP_WORDS) return PhoneCommand.OpenApp(name)
        }
        return null
    }

    /** Who to call or text: a name of at most a few words, or a number. Null = not a command. */
    private fun target(raw: String): String? {
        val t = raw.trim().replace(leadingArticle, "").trim()
        if (t.isEmpty()) return null
        if (dialable(t) != null) return t
        return t.takeIf { it.split(' ').size <= MAX_NAME_WORDS }
    }

    /** The digits of a spoken number ("333 123 4567", "+39 02 ..."), or null if it is a name. */
    fun dialable(text: String): String? {
        val digits = text.filter { it.isDigit() }
        if (digits.length < 3) return null
        if (text.any { it.isLetter() }) return null
        return (if (text.trim().startsWith("+")) "+" else "") + digits
    }

    fun answer(utterance: String): Answer? {
        val n = VoiceCommands.normalize(utterance)
        return when (n) {
            in yes -> Answer.YES
            in no -> Answer.NO
            else -> null
        }
    }

    private val numberRegex = Regex("(?<![\\w+])\\+?\\d[\\d \\u00A0./()-]{4,}\\d(?!\\w)")
    private val dateRegex = Regex("^\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}$")

    /** Phone numbers written in a text (for example Claude's last reply), in order, without repeats. */
    fun findPhoneNumbers(text: String, max: Int = 5): List<String> {
        val out = LinkedHashSet<String>()
        for (m in numberRegex.findAll(text)) {
            val raw = m.value.trim()
            if (dateRegex.matches(raw)) continue
            val digits = raw.filter { it.isDigit() }
            if (digits.length !in 6..15) continue
            out.add((if (raw.startsWith("+")) "+" else "") + digits)
            if (out.size >= max) break
        }
        return out.toList()
    }

    /** Lower case, no accents, no punctuation: "Nicolò D'Amico" -> "nicolo d amico". */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** How well a contact or app name matches what was said: 0 (no) to 100 (exact). */
    fun score(query: String, name: String): Int {
        val q = fold(query)
        val n = fold(name)
        if (q.isEmpty() || n.isEmpty()) return 0
        if (n == q) return 100
        val words = n.split(' ')
        if (n.startsWith("$q ")) return 85
        if (q in words) return 75
        val qWords = q.split(' ')
        if (qWords.all { w -> words.any { it == w } }) return 70
        if (qWords.all { w -> words.any { it.startsWith(w) } }) return 55
        if (n.contains(q)) return 40
        return 0
    }

    /** Matches sorted best first; only items with some match are kept. */
    fun <T> rank(query: String, items: List<T>, name: (T) -> String): List<Pair<T, Int>> =
        items.map { it to score(query, name(it)) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { fold(name(it.first)) })

    /** Score a match must reach to act on it without asking which one. */
    const val SURE_MATCH = 70
}
