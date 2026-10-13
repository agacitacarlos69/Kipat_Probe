package com.kipat.probe.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small set of original Kipat glyphs. The Kipat mark here is an abstract placeholder (a dot with
 * two arcs — "a tap radiating outward"); there is no finalized logo yet.
 */
object KipatIcons {

    val Mark: ImageVector by lazy {
        ImageVector.Builder("KipatMark", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // centre dot
                moveTo(9.5f, 12f)
                arcTo(2.5f, 2.5f, 0f, true, true, 14.5f, 12f)
                arcTo(2.5f, 2.5f, 0f, true, true, 9.5f, 12f)
                close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(7.2f, 16.8f)
                arcTo(6.8f, 6.8f, 0f, false, true, 7.2f, 7.2f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(16.8f, 7.2f)
                arcTo(6.8f, 6.8f, 0f, false, true, 16.8f, 16.8f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(19.6f, 4.4f)
                arcTo(10.8f, 10.8f, 0f, false, true, 19.6f, 19.6f)
            }
        }.build()
    }

    /** Tap / contactless waves used for the Touch tab. */
    val Touch: ImageVector by lazy {
        ImageVector.Builder("KipatTouch", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(6f, 8.5f)
                curveTo(7.6f, 10.6f, 7.6f, 13.4f, 6f, 15.5f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(10f, 6f)
                curveTo(12.9f, 9.6f, 12.9f, 14.4f, 10f, 18f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(14f, 3.5f)
                curveTo(18.2f, 8.6f, 18.2f, 15.4f, 14f, 20.5f)
            }
        }.build()
    }

    /** Two overlapping people, for Connections. */
    val Connections: ImageVector by lazy {
        ImageVector.Builder("KipatConnections", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(5.8f, 8.5f)
                arcTo(3.2f, 3.2f, 0f, true, true, 12.2f, 8.5f)
                arcTo(3.2f, 3.2f, 0f, true, true, 5.8f, 8.5f)
                moveTo(3.5f, 19.5f)
                curveTo(4.2f, 16.6f, 6.4f, 15f, 9f, 15f)
                curveTo(11.6f, 15f, 13.8f, 16.6f, 14.5f, 19.5f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(15.5f, 5.2f)
                arcTo(3.2f, 3.2f, 0f, false, true, 15.5f, 11.4f)
                moveTo(17.5f, 14.6f)
                curveTo(19f, 15.3f, 20.1f, 16.6f, 20.5f, 18.5f)
            }
        }.build()
    }
}
