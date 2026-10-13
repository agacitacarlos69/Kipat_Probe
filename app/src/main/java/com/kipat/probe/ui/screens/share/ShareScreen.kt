package com.kipat.probe.ui.screens.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.model.CardState
import com.kipat.probe.nfc.KipatTagUri
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatFixedColors
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Share: the ways someone can receive your Kipat right now — card, QR, link — plus an honest
 * note on what's not built. Replaces the old Touch tab's consumer half.
 */
@Composable
fun ShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    val link = remember(identity.cardId) { KipatTagUri.build(identity.cardId) }
    val firstName = identity.displayName.substringBefore(' ')

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Share", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(KipatSpacing.s16))
            QrCode(
                content = link,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics { contentDescription = "QR code for $firstName's Kipat link" },
            )
            Spacer(Modifier.height(KipatSpacing.s16))
            Text(
                "Scan to open $firstName's Kipat",
                style = MaterialTheme.typography.bodyLarge,
                color = Kipat.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                link.removePrefix("https://"),
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeading("Other ways to share")
            ActionRow("Copy link", icon = KipatSymbols.Copy, onClick = { copyLink(context, link) })
            KipatDivider()
            ActionRow(
                "Share link",
                icon = KipatSymbols.Share,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TITLE, "${identity.displayName} on Kipat")
                        .putExtra(Intent.EXTRA_TEXT, link)
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
            KipatDivider()
            ActionRow(
                "Kipat Card",
                icon = KipatSymbols.Nfc,
                supporting = if (card.state == CardState.ACTIVE) "Hold your card to the back of their phone" else "Your card isn't active",
                value = card.state.label,
                valueColor = if (card.state == CardState.ACTIVE) Kipat.colors.verified else Kipat.colors.warning,
            )
            KipatDivider()
            ActionRow(
                "Phone to phone",
                icon = KipatSymbols.Contactless,
                supporting = "Not available yet",
                enabled = false,
            )
            Text(
                "This prototype has no web profile yet, so the link and QR open kipat.app in a browser.",
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(KipatSpacing.gutter),
            )
        }
    }
}

/** QR rendered natively from a ZXing bit matrix; encoding runs off the main thread. */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix by produceState<BitMatrix?>(null, content) {
        value = withContext(Dispatchers.Default) {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }
    }
    // QR stays dark-on-white in both themes so every scanner reads it.
    Box(
        modifier
            .size(232.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(KipatFixedColors.QrPaper)
            .padding(KipatSpacing.s16),
    ) {
        val m = matrix ?: return@Box
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / m.width
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (m.get(x, y)) drawRect(KipatFixedColors.QrInk, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private fun copyLink(context: Context, link: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Kipat link", link))
    // Android 13+ shows its own clipboard confirmation; older versions need one.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
