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
    val label: String,
    val detail: String,
    val tone: StatusTone,
    /** True only when the UI may show a "verified" check mark. */
    val showsCheck: Boolean,
)

/**
 * Single source of truth for how an authenticity state is described to people.
 * Demo data is always labelled as demo so the prototype never pretends to be verified.
 */
fun AuthenticityState.present(isDemo: Boolean): AuthenticityPresentation = when (this) {
    AuthenticityState.AUTHENTIC -> if (isDemo) {
        AuthenticityPresentation("Verified · demo", "Sample data — no real verification ran", StatusTone.POSITIVE, showsCheck = true)
    } else {
        AuthenticityPresentation("Verified", "Card signature checked", StatusTone.POSITIVE, showsCheck = true)
    }
    AuthenticityState.CHECKING -> AuthenticityPresentation("Checking…", "Verifying this card", StatusTone.PENDING, showsCheck = false)
    AuthenticityState.UNVERIFIED -> AuthenticityPresentation("Unverified", "Details came from the tag and were not verified", StatusTone.WARNING, showsCheck = false)
    AuthenticityState.REVOKED -> AuthenticityPresentation("Revoked", "This card has been revoked by its owner", StatusTone.NEGATIVE, showsCheck = false)
    AuthenticityState.ERROR -> AuthenticityPresentation("Couldn't verify", "Verification failed — try again later", StatusTone.NEGATIVE, showsCheck = false)
    AuthenticityState.UNKNOWN -> AuthenticityPresentation(
        if (isDemo) "Not verified · demo" else "Not verified",
        "Verification isn't available yet",
        StatusTone.NEUTRAL,
        showsCheck = false,
    )
}
