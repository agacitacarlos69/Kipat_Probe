package com.kipat.probe.reveal

/**
 * Decides whether a Reveal launch is a repeat of the one just shown. Any external tap (NFC tag or
 * link) of the same card inside [windowMs] is one tap: it must not restart the Reveal or record a
 * second recent tap. In-app Preview and Replay always run. Pure, so it is unit tested.
 */
class TapDebouncer(private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private var lastCardId: String? = null
    private var lastAt: Long = 0L

    fun isDuplicate(cardId: String?, source: RevealSource, nowMs: Long): Boolean {
        val external = source == RevealSource.NFC_TAG || source == RevealSource.DEEP_LINK
        return external && cardId != null && cardId == lastCardId && nowMs - lastAt < windowMs
    }

    fun accept(cardId: String?, nowMs: Long) {
        lastCardId = cardId
        lastAt = nowMs
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 1_500L
    }
}
