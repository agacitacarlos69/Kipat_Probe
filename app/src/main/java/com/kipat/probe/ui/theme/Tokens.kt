package com.kipat.probe.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 — the only spacing values used in screens. */
object KipatSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp

    /** Screen gutter (Material compact margin). */
    val gutter = s16
    /** Minimum interactive size. */
    val touchTarget = 48.dp
    /** One-line / two-line list row minimum heights. */
    val rowOneLine = 56.dp
    val rowTwoLine = 72.dp
}

/** Radius by role, not one radius everywhere. */
object KipatRadius {
    val small = 8.dp      // chips
    val medium = 12.dp    // buttons, inputs, QR frame, menus
    val large = 16.dp     // recipient card, Reveal card
    val extraLarge = 20.dp // dialogs, sheets
}

val KipatShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(KipatRadius.small),
    medium = RoundedCornerShape(KipatRadius.medium),
    large = RoundedCornerShape(KipatRadius.large),
    extraLarge = RoundedCornerShape(KipatRadius.extraLarge),
)

/** Shell motion. The Reveal choreography lives in [com.kipat.probe.reveal.RevealTimeline]. */
object KipatMotion {
    const val Quick = 120
    const val Standard = 200
    const val TabFade = 150
    const val Push = 250

    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
