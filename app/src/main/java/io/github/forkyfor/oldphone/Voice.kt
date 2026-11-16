package io.github.forkyfor.oldphone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class VoiceCommand { NEW_CHAT, PROJECTS, BACK, STOP, READ }

/**
 * The handful of local navigation commands. Matching is on the whole utterance, in every
 * supported language at once, so anything longer is simply dictated into the chat.
 * To add a language, add its phrases here and its strings in res/values-xx.
 */
object VoiceCommands {
    private val phrases: Map<VoiceCommand, List<String>> = mapOf(
        VoiceCommand.NEW_CHAT to listOf(
            "new chat", "start a new chat", "open a new chat",
            "nuova chat", "apri nuova chat", "apri una nuova chat", "nuova conversazione",
        ),
        VoiceCommand.PROJECTS to listOf(
            "open projects", "show projects", "go to projects",
            "apri progetti", "apri i progetti", "vai ai progetti",
        ),
        VoiceCommand.BACK to listOf("back", "go back", "indietro", "torna indietro", "vai indietro"),
        VoiceCommand.STOP to listOf("stop reading", "stop talking", "ferma lettura", "ferma la lettura", "smetti di leggere"),
        VoiceCommand.READ to listOf("read aloud", "read the answer", "read the reply", "leggi la risposta", "leggi ad alta voce"),
    )

    fun normalize(utterance: String): String =
        utterance.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:\"“”]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun match(utterance: String): VoiceCommand? {
        val n = normalize(utterance)
        if (n.isEmpty()) return null
        return phrases.entries.firstOrNull { n in it.value }?.key
    }
}

/** Push-to-talk dictation with the system speech recogniser. Never listens in the background. */
class Dictation(private val ctx: Context, private val listener: Listener) {
    interface Listener {
        fun onListening(active: Boolean)
        fun onPartial(text: String)
        fun onFinal(text: String)
        /** One of the SpeechRecognizer.ERROR_* codes. */
        fun onError(code: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    var listening = false
        private set

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(locale: Locale) {
        if (listening) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
            it.setRecognitionListener(callbacks)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        listening = true
        listener.onListening(true)
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            finish()
            listener.onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun cancel() {
        if (!listening) return
        try { recognizer?.cancel() } catch (e: Exception) { }
        finish()
    }

    fun destroy() {
        try { recognizer?.destroy() } catch (e: Exception) { }
        recognizer = null
        listening = false
    }

    private fun finish() {
        if (!listening) return
        listening = false
        listener.onListening(false)
    }

    private fun best(b: Bundle?): String =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            val t = best(partialResults)
            if (listening && t.isNotEmpty()) listener.onPartial(t)
        }

        override fun onResults(results: Bundle?) {
            if (!listening) return
            finish()
            val t = best(results)
            if (t.isEmpty()) listener.onError(SpeechRecognizer.ERROR_NO_MATCH) else listener.onFinal(t)
        }

        override fun onError(error: Int) {
            if (!listening) return
            finish()
            listener.onError(error)
        }
    }
}

/** Optional read-aloud with the system text-to-speech engine. Created on first use only. */
class Speaker(private val ctx: Context, private val onState: (Boolean) -> Unit, private val onUnavailable: () -> Unit) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Locale>? = null
    private val main = Handler(Looper.getMainLooper())
    var speaking = false
        private set

    fun speak(text: String, locale: Locale) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null) {
            pending = text to locale
            tts = TextToSpeech(ctx.applicationContext) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    val p = pending
                    pending = null
                    if (!ready) {
                        tts = null
                        onUnavailable()
                    } else if (p != null) {
                        speakNow(p.first, p.second)
                    }
                }
            }
            return
        }
        if (ready) speakNow(text, locale) else pending = text to locale
    }

    private fun speakNow(text: String, locale: Locale) {
        val engine = tts ?: return
        if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) engine.language = locale
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "last") main.post { setSpeaking(false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { setSpeaking(false) }
            }
        })
        val chunks = chunk(text, (TextToSpeech.getMaxSpeechInputLength() - 100).coerceIn(500, 3500))
        setSpeaking(true)
        chunks.forEachIndexed { i, part ->
            engine.speak(
                part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null,
                if (i == chunks.lastIndex) "last" else "part$i"
            )
        }
    }

    fun stop() {
        pending = null
        try { tts?.stop() } catch (e: Exception) { }
        setSpeaking(false)
    }

    fun shutdown() {
        stop()
        try { tts?.shutdown() } catch (e: Exception) { }
        tts = null
        ready = false
    }

    private fun setSpeaking(v: Boolean) {
        if (speaking == v) return
        speaking = v
        onState(v)
    }

    companion object {
        /** Splits long text at sentence ends so each piece fits the engine's input limit. */
        fun chunk(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            var rest = text.trim()
            while (rest.length > max) {
                var cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), max - 1)
                if (cut < max / 2) cut = rest.lastIndexOf(' ', max - 1)
                if (cut < max / 2) cut = max - 1
                out.add(rest.substring(0, cut + 1).trim())
                rest = rest.substring(cut + 1).trim()
            }
            if (rest.isNotEmpty()) out.add(rest)
            return out
        }
    }
}
