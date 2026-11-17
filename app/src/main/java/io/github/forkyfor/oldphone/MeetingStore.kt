package io.github.forkyfor.oldphone

import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * A meeting waiting to reach the owner's "Meetings" project in Claude.
 *
 * Life cycle: RECORDED (audio on the phone, waiting for NotebookLM) -> READY (result accepted,
 * audio deleted, a .md file ready for Claude) -> deleted once the owner has added it to Claude.
 * Nothing stays on the phone after that.
 */
class Meeting(val id: String) {
    var created = 0L
    var durationMs = 0L
    var state = RECORDED
    var title = ""
    var description = ""
    var summary = ""
    /** Name of the .md file in the meetings folder, once READY. */
    var noteFile = ""
    /** Copy of the audio in Downloads (content URI), when the owner exported it for NotebookLM. */
    var exportedAudio = ""
    var delivered = false

    companion object {
        const val RECORDED = "recorded"
        const val READY = "ready"
    }
}

/** Plain files in one private folder: <id>.m4a, <id>.properties and the .md notes. */
class MeetingStore(val dir: File) {
    init {
        dir.mkdirs()
    }

    fun audioFile(id: String) = File(dir, "$id.m4a")
    private fun metaFile(id: String) = File(dir, "$id.properties")
    fun noteFile(m: Meeting): File? = m.noteFile.takeIf { it.isNotEmpty() }?.let { File(dir, it) }

    fun newId(now: Long = System.currentTimeMillis()): String = "m$now"

    fun save(m: Meeting) {
        val p = Properties()
        p["created"] = m.created.toString()
        p["duration"] = m.durationMs.toString()
        p["state"] = m.state
        p["title"] = m.title
        p["description"] = m.description
        p["summary"] = m.summary
        p["note"] = m.noteFile
        p["exported"] = m.exportedAudio
        p["delivered"] = m.delivered.toString()
        metaFile(m.id).writer(Charsets.UTF_8).use { p.store(it, null) }
    }

    fun load(id: String): Meeting? {
        val f = metaFile(id)
        if (!f.exists()) return null
        val p = Properties()
        try {
            f.reader(Charsets.UTF_8).use { p.load(it) }
        } catch (e: Exception) {
            return null
        }
        return Meeting(id).apply {
            created = p.getProperty("created", "0").toLongOrNull() ?: 0L
            durationMs = p.getProperty("duration", "0").toLongOrNull() ?: 0L
            state = p.getProperty("state", Meeting.RECORDED)
            title = p.getProperty("title", "")
            description = p.getProperty("description", "")
            summary = p.getProperty("summary", "")
            noteFile = p.getProperty("note", "")
            exportedAudio = p.getProperty("exported", "")
            delivered = p.getProperty("delivered", "false") == "true"
        }
    }

    /** Newest first. */
    fun list(): List<Meeting> =
        (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".properties") }
            .mapNotNull { load(it.name.removeSuffix(".properties")) }
            .sortedByDescending { it.created }

    fun deleteAudio(m: Meeting) {
        audioFile(m.id).delete()
    }

    /** Removes everything about a meeting from the phone. */
    fun delete(m: Meeting) {
        audioFile(m.id).delete()
        noteFile(m)?.delete()
        metaFile(m.id).delete()
    }
}

/** Pure text helpers for meetings (unit tested). */
object MeetingText {
    data class Result(val title: String, val description: String, val summary: String)

    private val label = Regex(
        "^[\\s#>*_-]*(titolo|title|descrizione|description|riassunto|summary|sommario)[\\s*_]*[:：][\\s*_]*(.*)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Reads NotebookLM's answer. Expected "TITOLO: … / DESCRIZIONE: … / RIASSUNTO: …" (or the English
     * labels, with or without Markdown). Without labels: everything is the summary, the title is
     * the first line, the description the first sentence.
     */
    fun parse(text: String): Result {
        val sections = HashMap<String, StringBuilder>()
        var current: String? = null
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val m = label.find(raw)
            if (m != null) {
                current = when (m.groupValues[1].lowercase(Locale.ROOT)) {
                    "titolo", "title" -> "t"
                    "descrizione", "description" -> "d"
                    else -> "s"
                }
                sections.getOrPut(current) { StringBuilder() }.append(m.groupValues[2]).append('\n')
            } else if (current != null) {
                sections.getValue(current).append(raw).append('\n')
            }
        }
        fun get(k: String) = sections[k]?.toString()?.trim() ?: ""
        var title = clean(get("t")).lineSequence().firstOrNull() ?: ""
        var description = clean(get("d"))
        var summary = get("s")
        if (sections.isEmpty()) summary = text.trim()
        if (summary.isEmpty()) summary = text.trim()
        if (title.isEmpty()) title = clean(summary.lineSequence().firstOrNull { it.isNotBlank() } ?: "")
        if (description.isEmpty()) description = firstSentence(summary)
        return Result(limitWords(title, 12).take(100), description.take(400), summary)
    }

    private fun clean(s: String) = s.trim().trim('*', '_', '"', '“', '”', '#', ' ', '-')

    private fun firstSentence(s: String): String {
        val flat = s.lineSequence().map { clean(it) }.filter { it.isNotBlank() }.joinToString(" ")
        val end = Regex("[.!?](\\s|$)").find(flat)?.range?.first
        return (if (end != null) flat.substring(0, end + 1) else flat).take(300)
    }

    private fun limitWords(s: String, max: Int): String {
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size <= max) words.joinToString(" ") else words.take(max).joinToString(" ") + "…"
    }

    /** "2026-11-17 Weekly planning.md" with characters that are safe everywhere. */
    fun fileName(date: String, title: String): String {
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), " ").replace(Regex("\\s+"), " ").trim().take(60)
        return "$date ${safe.ifEmpty { "meeting" }}.md"
    }

    /** The note added to the Claude project. Labels come from the app language. */
    fun toMarkdown(r: Result, dateText: String, durationText: String, labels: Labels): String = buildString {
        append("# ").append(r.title).append("\n\n")
        append("- ").append(labels.date).append(": ").append(dateText).append('\n')
        append("- ").append(labels.duration).append(": ").append(durationText).append("\n\n")
        if (r.description.isNotBlank()) append("**").append(labels.description).append(":** ").append(r.description).append("\n\n")
        append("## ").append(labels.summary).append("\n\n").append(r.summary.trim()).append('\n')
    }

    class Labels(val date: String, val duration: String, val description: String, val summary: String)

    /** "1 h 05 min", "23 min", "45 s". */
    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s >= 3600 -> "${s / 3600} h %02d min".format(Locale.ROOT, (s % 3600) / 60)
            s >= 60 -> "${s / 60} min"
            else -> "$s s"
        }
    }
}

/** Recognises the page of one Claude project, to link it as the "Meetings" project. */
object ProjectUrl {
    private val path = Regex("^/projects?/([0-9A-Za-z][0-9A-Za-z-]{7,})/?$")

    /** The normalised project address, or null if [url] is not a single project page. */
    fun match(url: String?): String? {
        if (url == null) return null
        val m = Regex("^https://([^/?#]+)(/[^?#]*)").find(url) ?: return null
        val host = m.groupValues[1].lowercase(Locale.ROOT)
        if (host != "claude.ai") return null
        if (path.find(m.groupValues[2]) == null) return null
        return "https://claude.ai" + m.groupValues[2].trimEnd('/')
    }
}
