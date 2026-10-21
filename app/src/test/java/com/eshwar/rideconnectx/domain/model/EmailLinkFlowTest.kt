package com.eshwar.rideconnectx.domain.model

import com.eshwar.rideconnectx.domain.model.EmailLinkFlow.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailLinkFlowTest {

    @Test fun `no link means nothing to finish`() {
        assertEquals(Step.NoLink, EmailLinkFlow.next(null, "a@b.co", "c@d.co"))
    }

    @Test fun `same phone finishes with the saved email`() {
        assertEquals(Step.Complete("a@b.co"), EmailLinkFlow.next("link", " a@b.co ", null))
    }

    @Test fun `another phone asks for the email`() {
        assertEquals(Step.AskEmail, EmailLinkFlow.next("link", null, null))
        assertEquals(Step.AskEmail, EmailLinkFlow.next("link", "", " "))
    }

    @Test fun `typed email wins over the saved one`() {
        assertEquals(Step.Complete("new@b.co"), EmailLinkFlow.next("link", "old@b.co", " new@b.co"))
    }

    @Test fun `only a wrong email keeps the link`() {
        assertTrue(EmailLinkFlow.linkStillGood("ERROR_INVALID_EMAIL"))
        assertFalse(EmailLinkFlow.linkStillGood("ERROR_EXPIRED_ACTION_CODE"))
        assertFalse(EmailLinkFlow.linkStillGood("ERROR_INVALID_ACTION_CODE"))
        assertFalse(EmailLinkFlow.linkStillGood(null))
    }

    @Test fun `errors are in plain words`() {
        assertTrue(EmailLinkFlow.message("ERROR_EXPIRED_ACTION_CODE")!!.contains("expired"))
        assertEquals(EmailLinkFlow.WRONG_EMAIL, EmailLinkFlow.message("ERROR_INVALID_EMAIL"))
        assertEquals(null, EmailLinkFlow.message("SOMETHING_ELSE"))
        assertEquals("Too many tries. Wait a few minutes and try again.", AuthError.TooManyTries.message)
    }

    private val min = 60_000L

    @Test fun `link expires after 10 minutes`() {
        assertFalse(EmailLinkFlow.isExpired(0, 9 * min + 59_000))
        assertTrue(EmailLinkFlow.isExpired(0, 10 * min + 1_000))
        assertFalse(EmailLinkFlow.isExpired(null, 99 * min))
    }

    @Test fun `resend waits 60 seconds`() {
        assertEquals(EmailLinkFlow.SendCheck.Wait(41), EmailLinkFlow.checkSend(listOf(0L), 19_000))
        assertEquals(EmailLinkFlow.SendCheck.Ok, EmailLinkFlow.checkSend(listOf(0L), 60_000))
        assertEquals(EmailLinkFlow.SendCheck.Ok, EmailLinkFlow.checkSend(emptyList(), 0))
    }

    @Test fun `sixth request in a day is capped until the oldest ages out`() {
        val five = listOf(0L, 2 * min, 4 * min, 6 * min, 8 * min)
        assertEquals(EmailLinkFlow.SendCheck.Capped(24 * 60 - 9), EmailLinkFlow.checkSend(five, 9 * min))
        assertEquals(EmailLinkFlow.SendCheck.Ok, EmailLinkFlow.checkSend(five, 24 * 60 * min + 1))
        assertEquals("Too many requests today. Try again in 51 minutes.", EmailLinkFlow.cappedMessage(51))
        assertEquals("Too many requests today. Try again in 1 minute.", EmailLinkFlow.cappedMessage(1))
        assertEquals("Too many requests today. Try again in 1 hour.", EmailLinkFlow.cappedMessage(60))
        assertEquals("Too many requests today. Try again in 24 hours.", EmailLinkFlow.cappedMessage(24 * 60 - 9))
    }

    @Test fun `email is masked`() {
        assertEquals("e***@gmail.com", EmailLinkFlow.maskEmail(" eshwar@gmail.com "))
        assertEquals("***@b.co", EmailLinkFlow.maskEmail("@b.co"))
        assertEquals("***", EmailLinkFlow.maskEmail("nope"))
    }

    @Test
    fun sendFailureIsPlainWords() {
        assertEquals(EmailLinkFlow.SEND_FAILED, EmailLinkFlow.sendMessage("ERROR_INVALID_HOSTING_LINK_DOMAIN"))
        assertEquals(EmailLinkFlow.SEND_FAILED, EmailLinkFlow.sendMessage(null))
        assertEquals(EmailLinkFlow.TOO_MANY, EmailLinkFlow.sendMessage("ERROR_TOO_MANY_REQUESTS"))
    }

    @Test
    fun quotaExceededGetsFriendlyMessage() {
        val q = EmailLinkFlow.QUOTA_BUSY
        assertEquals(q, EmailLinkFlow.sendMessage("ERROR_QUOTA_EXCEEDED"))
        assertEquals(q, EmailLinkFlow.sendMessage(null, "QUOTA_EXCEEDED : Exceeded daily quota for email sending."))
        assertEquals(q, EmailLinkFlow.sendMessage("ERROR_TOO_MANY_REQUESTS", "Quota exceeded"))
        assertEquals(q, EmailLinkFlow.sendMessage(null, "quota-exceeded"))
        assertEquals(EmailLinkFlow.TOO_MANY, EmailLinkFlow.sendMessage("ERROR_TOO_MANY_REQUESTS", "We have blocked all requests"))
        assertEquals(EmailLinkFlow.SEND_FAILED, EmailLinkFlow.sendMessage(null, null))
    }
}
