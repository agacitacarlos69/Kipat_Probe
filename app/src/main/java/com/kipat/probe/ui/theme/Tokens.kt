package com.kipat.probe.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4dp spacing grid. */
object KipatSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Horizontal gutter for screen content. */
    val gutter = 20.dp
    /** Minimum interactive size (Material/WCAG touch target). */
    val touchTarget = 48.dp
}

object KipatRadius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val pill = 999.dp
}

val KipatShapes = Shapes(
    extraSmall = RoundedCornerShape(KipatRadius.xs),
    small = RoundedCornerShape(KipatRadius.sm),
    medium = RoundedCornerShape(KipatRadius.md),
    large = RoundedCornerShape(KipatRadius.lg),
    extraLarge = RoundedCornerShape(KipatRadius.xl),
)

/** Motion tokens. Reveal choreography lives in [com.kipat.probe.reveal.RevealTimeline]. */
object KipatMotion {
    const val Quick = 120
    const val Standard = 220
    const val Emphasized = 360
    const val Ambient = 2400

    /** Decelerate into rest — default for things appearing. */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** Symmetric — for state changes of things already on screen. */
    val EaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    /** Accelerate away — for exits. */
    val EaseIn: Easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
}
