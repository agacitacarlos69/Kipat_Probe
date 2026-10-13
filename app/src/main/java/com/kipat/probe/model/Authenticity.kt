package com.kipat.probe.model

/**
 * Authenticity of a Kipat card/identity as known to this device.
 *
 * This is deliberately not a Boolean: the Reveal must never claim "verified" before a
 * verification actually happened. Production NTAG 424 / backend verification will move a
 * payload from [CHECKING] to [AUTHENTIC], [UNVERIFIED], [REVOKED] or [ERROR] asynchronously.
 */
enum class AuthenticityState(val wireId: String) {
    UNKNOWN("unknown"),
    CHECKING("checking"),
    AUTHENTIC("authentic"),
    UNVERIFIED("unverified"),
    REVOKED("revoked"),
    ERROR("error");

    companion object {
        fun fromWireId(value: String?): AuthenticityState =
            entries.firstOrNull { it.wireId.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** Visual tone used by status badges; mapped to theme colors in the UI layer. */
enum class StatusTone { POSITIVE, NEUTRAL, PENDING, WARNING, NEGATIVE }

data class AuthenticityPresentation(
    /** Short words shown next to the trust glyph, e.g. "Verified card". */
    val label: String,
    val detail: String,
    val tone: StatusTone,
    /** True only when the UI may show the filled verified seal. */
    val showsCheck: Boolean,
    /** "Sample data" for demo payloads — always shown on its own line, never merged into [label]. */
    val demoNote: String?,
)

/**
 * Single source of truth for how an authenticity state is described to people. Calm, plain
 * words; demo data is always disclosed separately so the prototype never pretends to be real.
 */
fun AuthenticityState.present(isDemo: Boolean): AuthenticityPresentation {
    val note = if (isDemo) "Sample data" else null
    return when (this) {
        AuthenticityState.AUTHENTIC -> AuthenticityPresentation("Verified card", if (isDemo) "Sample data. No real verification ran" else "Card signature checked", StatusTone.POSITIVE, showsCheck = true, demoNote = note)
        AuthenticityState.CHECKING -> AuthenticityPresentation("Checking card", "Verifying this card", StatusTone.PENDING, showsCheck = false, demoNote = note)
        AuthenticityState.UNVERIFIED -> AuthenticityPresentation("Unverified card", "Details came from the card and were not verified", StatusTone.WARNING, showsCheck = false, demoNote = note)
        AuthenticityState.REVOKED -> AuthenticityPresentation("Card revoked", "The owner revoked this card", StatusTone.NEGATIVE, showsCheck = false, demoNote = note)
        AuthenticityState.ERROR -> AuthenticityPresentation("Couldn't verify", "Verification failed. Try again later", StatusTone.NEGATIVE, showsCheck = false, demoNote = note)
        AuthenticityState.UNKNOWN -> AuthenticityPresentation("Not verified", "Verification isn't available yet", StatusTone.NEUTRAL, showsCheck = false, demoNote = note)
    }
}
