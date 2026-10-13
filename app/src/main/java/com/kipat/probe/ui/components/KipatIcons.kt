package com.kipat.probe.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Kipat mark: an abstract placeholder (a contact point with two arcs) until a final logo
 * exists. Used only in the Home app bar and the Share screen's card row. All other icons come
 * from [KipatSymbols].
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
}
