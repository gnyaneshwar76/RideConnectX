package com.eshwar.rideconnectx.presentation.viewmodel

import com.eshwar.rideconnectx.data.nav.AppTier
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAppListTest {
    private val label = { p: String -> p.split('.')[1] }

    @Test fun importantFirst_defaultsFollowTier_switchWins() {
        val list = buildNotificationApps(
            seen = setOf("com.instagram.android", "com.whatsapp", "com.discord"),
            switches = mapOf("com.discord" to true, "com.whatsapp" to false),
            defaultSms = null,
            labelOf = label,
        )
        assertEquals(listOf("com.whatsapp", "com.discord", "com.instagram.android"), list.map { it.pkg })
        assertEquals(listOf(false, true, false), list.map { it.on })
        assertEquals(AppTier.IMPORTANT, list[0].tier)
    }

    @Test fun unlistedCallApp_isImportantAndOn_neverIgnored() {
        val list = buildNotificationApps(setOf("com.some.dialer"), emptyMap(), null, label)
        assertEquals(AppTier.IMPORTANT, list.single().tier)
        assertEquals(true, list.single().on)
    }
}
