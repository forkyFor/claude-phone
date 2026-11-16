package io.github.forkyfor.oldphone

import io.github.forkyfor.oldphone.engine.EngineInfo
import io.github.forkyfor.oldphone.engine.UrlPolicy
import io.github.forkyfor.oldphone.engine.UrlPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test fun officialSiteStaysInside() {
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("https", "claude.ai"))
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("HTTPS", "Claude.AI"))
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("https", "sub.claude.ai"))
    }

    @Test fun lookAlikeHostsLeaveTheApp() {
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "claude.ai.evil.example"))
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "notclaude.ai"))
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "example.com"))
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "www.anthropic.com"))
    }

    @Test fun plainHttpIsNeverLoadedInside() {
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("http", "claude.ai"))
    }

    @Test fun signInProvidersAreAllowed() {
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("https", "accounts.google.com"))
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("https", "appleid.apple.com"))
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "google.com"))
    }

    @Test fun ownerCanAddSsoHosts() {
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "login.corp.example"))
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("https", "login.corp.example", setOf("login.corp.example")))
        assertEquals(Decision.INTERNAL, UrlPolicy.decide("https", "a.login.corp.example", setOf("login.corp.example")))
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("https", "corp.example", setOf("login.corp.example")))
    }

    @Test fun dangerousSchemesAreBlocked() {
        for (s in listOf("javascript", "file", "content", "intent", "market", null)) {
            assertEquals(Decision.BLOCK, UrlPolicy.decide(s, "claude.ai"))
        }
        assertEquals(Decision.BLOCK, UrlPolicy.decide("https", null))
        assertEquals(Decision.EXTERNAL, UrlPolicy.decide("mailto", null))
    }

    @Test fun pastedLinksMustBeOfficialHttps() {
        assertTrue(UrlPolicy.isAcceptablePastedLink("https", "claude.ai"))
        assertFalse(UrlPolicy.isAcceptablePastedLink("http", "claude.ai"))
        assertFalse(UrlPolicy.isAcceptablePastedLink("https", "claude.ai.example.com"))
        assertFalse(UrlPolicy.isAcceptablePastedLink("https", "accounts.google.com"))
    }

    @Test fun webViewVersionParsing() {
        assertEquals(138, EngineInfo.parseMajor("138.0.7204.179"))
        assertEquals(0, EngineInfo.parseMajor(null))
        assertEquals(0, EngineInfo.parseMajor("unknown"))
        assertEquals(
            95,
            EngineInfo.majorFromUserAgent("Mozilla/5.0 (Linux; Android 5.1; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/95.0.4638.74 Mobile Safari/537.36")
        )
        assertEquals(0, EngineInfo.majorFromUserAgent("Mozilla/5.0 (Android 12; Mobile; rv:160.0) Gecko/160.0 Firefox/160.0"))
    }
}
