package com.kipat.probe.model

enum class CardState(val label: String) {
    ACTIVE("Active"),
    NOT_PROVISIONED("Not provisioned"),
    SUSPENDED("Suspended"),
    REVOKED("Revoked"),
}

/** The physical Kipat Card bound to the active identity. */
data class KipatCard(
    val cardId: String,
    val state: CardState,
    val authenticity: AuthenticityState,
    val isDemo: Boolean,
)

/** Placeholder for Kipat Plus. Billing is intentionally not implemented in this milestone. */
enum class Entitlement(val label: String, val detail: String) {
    FREE("Kipat Free", "Core identity and Reveal"),
    PLUS("Kipat Plus", "All Reveal templates and themes"),
}

/** A Reveal that happened. Distinct from a [Connection]: taps are never auto-saved. */
data class RecentTap(
    val id: String,
    val cardId: String,
    val displayName: String,
    val subtitle: String,
    val authenticity: AuthenticityState,
    val atMillis: Long,
    val savedAsConnection: Boolean,
    val isDemo: Boolean,
    /** How this encounter happened (phone tap, card, link); kept so a later Connect remembers it. */
    val source: ConnectionSource = ConnectionSource.NFC_CARD,
)
