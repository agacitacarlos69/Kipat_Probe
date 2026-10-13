package com.kipat.probe.data

import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.CardState
import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSource
import com.kipat.probe.model.Identity
import com.kipat.probe.model.IdentityType
import com.kipat.probe.model.KipatCard
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.model.RecentTap
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme

/**
 * First-run demo content. Everything here is local sample data and is flagged `isDemo` so the
 * UI never presents it as verified production state.
 */
object SampleData {
    const val DEMO_CARD_ID = "KPT-DEMO-0001"
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    val identity = Identity(
        id = "id-vash",
        cardId = DEMO_CARD_ID,
        type = IdentityType.STUDENT,
        profileTemplate = ProfileTemplateId.STUDENT_MINIMAL,
        revealTemplate = RevealTemplateId.SIGNATURE_DRIP,
        revealTheme = RevealTheme.NIGHT,
        fields = mapOf(
            ProfileField.DISPLAY_NAME to "Vash Puno",
            ProfileField.PROGRAM to "BS Computer Science",
            ProfileField.UNIVERSITY to "Polytechnic University of the Philippines",
            ProfileField.BIO to "Building Kipat. Identity in a tap.",
            ProfileField.GITHUB to "vashpuno",
            ProfileField.ROLE to "Founder",
            ProfileField.COMPANY to "Kipat",
        ),
    )

    val card = KipatCard(DEMO_CARD_ID, CardState.ACTIVE, AuthenticityState.AUTHENTIC, isDemo = true)

    fun connections(now: Long): List<Connection> = listOf(
        Connection("c-1", "KPT-DEMO-0102", "Alyssa Ramos", "UX Designer", "Globe Labs", AuthenticityState.AUTHENTIC, ConnectionSource.NFC_CARD, now - 2 * HOUR, pinned = true, revealTemplate = RevealTemplateId.PARTICLE_BLOOM, revealTheme = RevealTheme.AURORA),
        Connection("c-2", "KPT-DEMO-0103", "Miguel Santos", "BS Information Technology", "University of the Philippines", AuthenticityState.AUTHENTIC, ConnectionSource.PHONE_TAP, now - 5 * HOUR),
        Connection("c-3", "KPT-DEMO-0104", "Bea Villanueva", "Product Manager", "Maya", AuthenticityState.UNVERIFIED, ConnectionSource.QR, now - 1 * DAY, revealTemplate = RevealTemplateId.MINIMAL_FADE, revealTheme = RevealTheme.MONO),
        Connection("c-4", "KPT-DEMO-0105", "Daniel Cruz", "Android Engineer", "Kipat", AuthenticityState.AUTHENTIC, ConnectionSource.NFC_CARD, now - 3 * DAY, pinned = true),
        Connection("c-5", "KPT-DEMO-0106", "Kim Aquino", "Event Host", "DevFest Manila", AuthenticityState.UNKNOWN, ConnectionSource.EVENT, now - 6 * DAY, revealTemplate = RevealTemplateId.RIPPLE, revealTheme = RevealTheme.EMBER),
        Connection("c-6", "KPT-DEMO-0107", "Rafael Lim", "Creator", "Studio Lim", AuthenticityState.REVOKED, ConnectionSource.LINK, now - 12 * DAY),
    )

    fun recentTaps(now: Long): List<RecentTap> = listOf(
        RecentTap("t-1", "KPT-DEMO-0102", "Alyssa Ramos", "UX Designer, Globe Labs", AuthenticityState.AUTHENTIC, now - 2 * HOUR, savedAsConnection = true, isDemo = true),
        RecentTap("t-2", "KPT-DEMO-0201", "Jonas Reyes", "BS Architecture, UST", AuthenticityState.UNVERIFIED, now - 4 * HOUR, savedAsConnection = false, isDemo = true),
        RecentTap("t-3", "KPT-DEMO-0103", "Miguel Santos", "BS Information Technology", AuthenticityState.AUTHENTIC, now - 5 * HOUR, savedAsConnection = true, isDemo = true),
    )
}
