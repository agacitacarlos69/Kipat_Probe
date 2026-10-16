package com.kipat.probe.ui.screens.profile

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.cards.CardLinkSession
import com.kipat.probe.cards.CardLinkState
import com.kipat.probe.cards.KipatCardId
import com.kipat.probe.cards.KipatCards
import com.kipat.probe.cards.LinkedCardMessage
import com.kipat.probe.cards.LinkedCardStatus
import com.kipat.probe.cards.LinkedKipatCard
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.nfc.NfcAvailability
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import java.text.DateFormat
import java.util.Date

/** Shown wherever cards are explained: what a linked card does, and the prototype's limit. */
const val LINKED_CARD_EXPLAINER = "Your cards show your current Kipat profile and Reveal. Changes apply everywhere you share this Kipat identity."
const val LINKED_CARD_PROTOTYPE_NOTE = "Prototype: a linked card opens your profile on this phone. Other phones will open it once Kipat's online service is available."

/** The Home / Share row for the person's own cards, from the linked-card registry (never a demo state). */
data class CardSummary(val active: Int, val supporting: String, val value: String)

@Composable
fun rememberCardSummary(activeLine: String = "Shows your current Kipat profile"): CardSummary {
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val all by KipatCards.cards.collectAsStateWithLifecycle()
    val mine = all.filter { it.ownerIdentityId == identity.id && it.status != LinkedCardStatus.REVOKED }
    val active = mine.count { it.status == LinkedCardStatus.ACTIVE }
    return when {
        mine.isEmpty() -> CardSummary(0, "Link a card to share by touch", "Not linked")
        active == 0 -> CardSummary(0, "Your card is turned off", "Off")
        else -> CardSummary(active, activeLine, if (active == 1) "Active" else "$active active")
    }
}

private fun LinkedKipatCard.statusLine(): String = when (status) {
    LinkedCardStatus.ACTIVE -> lastSeenAt?.let { "Last tap ${relativeTime(it)}" } ?: "Not tapped yet"
    LinkedCardStatus.DISABLED -> "Turned off"
    LinkedCardStatus.REVOKED -> "Removed"
}

/** Profile → Kipat cards. */
@Composable
fun KipatCardsScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val all by KipatCards.cards.collectAsStateWithLifecycle()
    val cards = all.filter { it.ownerIdentityId == identity.id && it.status != LinkedCardStatus.REVOKED }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Kipat cards", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            Text(
                LINKED_CARD_EXPLAINER,
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
            )
            SectionHeading("My Kipat cards")
            if (cards.isEmpty()) {
                ActionRow("No cards linked yet", supporting = "Link an NFC card to share your Kipat by touch")
            }
            cards.forEachIndexed { i, card ->
                if (i > 0) KipatDivider()
                ActionRow(
                    card.displayName,
                    icon = KipatSymbols.IdCard,
                    supporting = card.statusLine(),
                    value = card.maskedId,
                    chevron = true,
                    onClick = { onOpen(card.cardId) },
                )
            }
            KipatPrimaryButton(
                "Add Kipat card",
                icon = KipatSymbols.Nfc,
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KipatSpacing.gutter),
            )
            Text(
                LINKED_CARD_PROTOTYPE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            )
        }
    }
}

/** Profile → Kipat cards → a card. Presentation comes from the identity; nothing to style here. */
@Composable
fun KipatCardDetailScreen(cardId: String, onBack: () -> Unit, onRevealStyle: () -> Unit) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val all by KipatCards.cards.collectAsStateWithLifecycle()
    val card = all.firstOrNull { it.cardId == cardId }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var showDeveloper by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar(card?.displayName ?: "Kipat card", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            if (card == null) {
                ActionRow("This card isn't linked on this phone")
                return@Column
            }
            ActionRow(
                "Status",
                value = card.status.label,
                valueColor = if (card.status == LinkedCardStatus.ACTIVE) Kipat.colors.verified else Kipat.colors.warning,
            )
            KipatDivider()
            ActionRow("Identity", value = identity.displayName)

            SectionHeading("Presentation")
            Text(
                "Uses your current Kipat profile",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s4),
            )
            ActionRow("Reveal", supporting = "Set for your whole Kipat", value = identity.revealTemplate.label, chevron = true, onClick = onRevealStyle)

            SectionHeading("Card")
            ActionRow("Card ID", value = card.maskedId)
            KipatDivider()
            ActionRow("Last tap", value = card.lastSeenAt?.let { relativeTime(it) } ?: "Not yet")
            KipatDivider()
            ActionRow("Authenticity", supporting = "Prototype cards can be copied, so they show as unverified", value = "Unverified")

            if (card.status != LinkedCardStatus.REVOKED) {
                Column(Modifier.padding(KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
                    if (card.status == LinkedCardStatus.ACTIVE) {
                        KipatSecondaryButton("Disable card", onClick = { KipatCards.setStatus(card.cardId, LinkedCardStatus.DISABLED) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        KipatSecondaryButton("Enable card", onClick = { KipatCards.setStatus(card.cardId, LinkedCardStatus.ACTIVE) }, modifier = Modifier.fillMaxWidth())
                    }
                    KipatTextButton("Remove card", color = Kipat.colors.revoked, onClick = { confirmRemove = true })
                }
                Text(
                    "A disabled card shows no profile when tapped. You can enable it again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kipat.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
                )
            }

            ActionRow(
                "Developer details",
                icon = KipatSymbols.DeveloperMode,
                chevron = false,
                value = if (showDeveloper) "Hide" else "Show",
                onClick = { showDeveloper = !showDeveloper },
                modifier = Modifier.padding(top = KipatSpacing.s16),
            )
            if (showDeveloper) {
                val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                ActionRow("Card type", value = card.cardType.label)
                KipatDivider()
                ActionRow("NDEF message", value = "${LinkedCardMessage.size(card.cardId, context.packageName)} bytes")
                KipatDivider()
                ActionRow("Linked", value = dates.format(Date(card.createdAt)))
                KipatDivider()
                ActionRow("Last written", value = dates.format(Date(card.lastSyncedAt)))
                KipatDivider()
                ActionRow("Resolver", value = "This phone only")
                SelectionContainer {
                    Text(
                        KipatCardId.uri(card.cardId),
                        style = mono,
                        color = Kipat.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
                    )
                }
            }
        }
    }

    if (confirmRemove && card != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this card?") },
            text = { Text("It will no longer open your Kipat. A removed card can be linked again later as a new card.") },
            confirmButton = {
                KipatTextButton("Remove", color = Kipat.colors.revoked, onClick = {
                    KipatCards.setStatus(card.cardId, LinkedCardStatus.REVOKED)
                    confirmRemove = false
                    onBack()
                })
            },
            dismissButton = { KipatTextButton("Cancel", onClick = { confirmRemove = false }) },
            containerColor = Kipat.colors.surfaceElevated,
        )
    }
}

/** Profile → Kipat cards → Add Kipat card: hold card, link, read back, done. */
@Composable
fun AddKipatCardScreen(onBack: () -> Unit, onOpenCard: (String) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val session = remember(activity) { activity?.let { CardLinkSession(it, scope) { KipatRepository.identity.value.id } } }
    val state by (session?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<CardLinkState>(CardLinkState.Waiting) }).collectAsStateWithLifecycle()
    var nfc by remember { mutableStateOf(session?.availability() ?: NfcAvailability.UNSUPPORTED) }

    LifecycleResumeEffect(session) {
        nfc = session?.availability() ?: NfcAvailability.UNSUPPORTED
        session?.onResume()
        onPauseOrDispose { session?.onPause() }
    }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Add Kipat card", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            when {
                nfc == NfcAvailability.UNSUPPORTED -> Message("This phone doesn't have NFC", "A Kipat card is linked by holding it to a phone with NFC.")
                nfc == NfcAvailability.DISABLED -> {
                    Message("Turn on NFC", "NFC is off. Turn it on to link a card.")
                    KipatPrimaryButton(
                        "Open NFC settings",
                        onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter),
                    )
                }
                else -> when (val s = state) {
                    CardLinkState.Waiting -> {
                        Message("Hold your card to the back of the phone", "Uses your current Kipat profile")
                        Text(
                            "The card stores only a private link to your Kipat. Your name and profile stay in the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Kipat.colors.textTertiary,
                            modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
                        )
                    }
                    is CardLinkState.Working -> Message(s.label, "Keep the card still")
                    is CardLinkState.ConfirmReplace -> Message("Keep the card on the phone", "Waiting for your answer")
                    is CardLinkState.Linked -> {
                        Message("Card linked", "Uses your current Kipat profile")
                        Column(Modifier.padding(KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
                            KipatPrimaryButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
                            KipatSecondaryButton("View card", onClick = { onOpenCard(s.card.cardId) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is CardLinkState.AlreadyLinked -> {
                        Message("This card is already linked", "It already opens your Kipat. Nothing was changed.")
                        Column(Modifier.padding(KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
                            KipatPrimaryButton("View card", onClick = { onOpenCard(s.card.cardId) }, modifier = Modifier.fillMaxWidth())
                            KipatSecondaryButton("Link another card", onClick = { session?.retry() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is CardLinkState.Failed -> {
                        Message(s.title, s.detail ?: "Try again.")
                        KipatPrimaryButton("Try again", onClick = { session?.retry() }, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
                    }
                }
            }
        }
    }

    (state as? CardLinkState.ConfirmReplace)?.let { confirm ->
        AlertDialog(
            onDismissRequest = { session?.retry() },
            title = { Text("Replace what's on this card?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
                    Text("This card already holds data. Linking it to your Kipat replaces it.")
                    confirm.existing.take(4).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary) }
                }
            },
            confirmButton = { KipatTextButton("Replace and link", color = Kipat.colors.revoked, onClick = { session?.confirmReplace() }) },
            dismissButton = { KipatTextButton("Cancel", onClick = { session?.retry() }) },
            containerColor = Kipat.colors.surfaceElevated,
        )
    }
}

@Composable
private fun Message(title: String, detail: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s24)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Kipat.colors.textPrimary, modifier = Modifier.semantics { heading() })
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textSecondary)
    }
}
