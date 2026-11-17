package io.github.forkyfor.oldphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class MeetingTest {
    @Test fun parsesLabelledItalianAnswer() {
        val r = MeetingText.parse(
            """
            **TITOLO:** Pianificazione lancio app
            **DESCRIZIONE:** Riunione del team per decidere date e responsabili del lancio.
            **RIASSUNTO:**
            - Lancio fissato al 3 dicembre
            - Marco prepara la pagina
            """.trimIndent()
        )
        assertEquals("Pianificazione lancio app", r.title)
        assertEquals("Riunione del team per decidere date e responsabili del lancio.", r.description)
        assertTrue(r.summary.startsWith("- Lancio fissato al 3 dicembre"))
        assertTrue(r.summary.contains("Marco prepara la pagina"))
    }

    @Test fun parsesEnglishAndHeadings() {
        val r = MeetingText.parse("## Title: Budget review\nDescription: Quarterly numbers.\nSummary: Costs are down 4%.\nNext steps agreed.")
        assertEquals("Budget review", r.title)
        assertEquals("Quarterly numbers.", r.description)
        assertEquals("Costs are down 4%.\nNext steps agreed.", r.summary)
    }

    @Test fun answerWithoutLabelsStillGivesSuggestions() {
        val r = MeetingText.parse("Incontro con il fornitore\nAbbiamo concordato i prezzi. Consegna a gennaio.")
        assertEquals("Incontro con il fornitore", r.title)
        assertEquals("Incontro con il fornitore Abbiamo concordato i prezzi.", r.description)
        assertTrue(r.summary.contains("Consegna a gennaio"))
    }

    @Test fun longTitlesAreShortened() {
        val r = MeetingText.parse("TITOLO: " + (1..30).joinToString(" ") { "parola$it" } + "\nRIASSUNTO: x")
        assertTrue(r.title.endsWith("…"))
        assertEquals(12, r.title.split(" ").size)
    }

    @Test fun markdownNoteAndFileName() {
        val md = MeetingText.toMarkdown(
            MeetingText.Result("Lancio", "Date e ruoli.", "- Lancio il 3/12"), "17/11/2026 10:30", "23 min",
            MeetingText.Labels("Data", "Durata", "Descrizione", "Riassunto")
        )
        assertTrue(md.startsWith("# Lancio\n"))
        assertTrue(md.contains("- Durata: 23 min"))
        assertTrue(md.contains("**Descrizione:** Date e ruoli."))
        assertTrue(md.contains("## Riassunto\n\n- Lancio il 3/12"))
        assertEquals("2026-11-17 Lancio Q4 piano.md", MeetingText.fileName("2026-11-17", "Lancio: Q4/piano?"))
        assertEquals("23 min", MeetingText.duration(23 * 60_000L + 5_000))
        assertEquals("1 h 05 min", MeetingText.duration(65 * 60_000L))
        assertEquals("45 s", MeetingText.duration(45_000))
    }

    @Test fun projectAddresses() {
        assertEquals(
            "https://claude.ai/project/0198a1b2-c3d4-7e8f-9a0b-1c2d3e4f5a6b",
            ProjectUrl.match("https://claude.ai/project/0198a1b2-c3d4-7e8f-9a0b-1c2d3e4f5a6b?tab=files")
        )
        assertNull(ProjectUrl.match("https://claude.ai/projects"))
        assertNull(ProjectUrl.match("https://claude.ai/chat/0198a1b2-c3d4-7e8f-9a0b-1c2d3e4f5a6b"))
        assertNull(ProjectUrl.match("https://evil.example/project/0198a1b2-c3d4-7e8f-9a0b-1c2d3e4f5a6b"))
        assertNull(ProjectUrl.match(null))
    }

    @Test fun storeKeepsNothingAfterDelete() {
        val dir = Files.createTempDirectory("meetings").toFile()
        val store = MeetingStore(dir)
        val m = Meeting(store.newId(1000L)).apply {
            created = 1000L
            durationMs = 5000L
            title = "Riunione – àèì"
        }
        store.audioFile(m.id).writeText("audio")
        store.save(m)
        val back = store.load(m.id)!!
        assertEquals("Riunione – àèì", back.title)
        assertEquals(Meeting.RECORDED, back.state)
        assertEquals(1, store.list().size)
        store.deleteAudio(back)
        assertFalse(store.audioFile(m.id).exists())
        store.delete(back)
        assertTrue(store.list().isEmpty())
        assertTrue((dir.listFiles() ?: emptyArray()).isEmpty())
    }

    @Test fun meetingVoiceCommands() {
        assertEquals(PhoneCommand.MeetingStart, PhoneCommands.parse("Registra la riunione"))
        assertEquals(PhoneCommand.MeetingStop, PhoneCommands.parse("ferma la registrazione"))
        assertEquals(PhoneCommand.MeetingsOpen, PhoneCommands.parse("apri le riunioni"))
        assertEquals(PhoneCommand.MeetingStart, PhoneCommands.parse("record the meeting"))
        assertEquals(PhoneCommand.MeetingStop, PhoneCommands.parse("stop recording"))
    }
}
