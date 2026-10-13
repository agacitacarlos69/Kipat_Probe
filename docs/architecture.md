# Architecture

Single `:app` module, Kotlin + Jetpack Compose, no DI framework (one in-memory repository is
the only shared dependency).

## Layers

| Package | Responsibility | Android-free? |
|---|---|---|
| `model/` | Identity, profile fields/templates, connections, card, taps, authenticity + its presentation, profile validation | Yes |
| `reveal/RevealTemplate.kt`, `RevealEngine.kt`, `RevealPayload.kt`, `RevealStyleDraft.kt` | Six-template family and its `RevealChoreography`, `RevealTimeline`, Kipat motion curves, frame math, payload + Intent codec, picker draft | Yes |
| `nfc/NfcIntentParser.kt`, `NfcStatus.kt` | Kipat URI parsing, tag URI builder, write states | Yes |
| `data/` | `SampleData`, `KipatRepository` (StateFlows), `RevealResolver` (tap → payload) | Yes |
| `nfc/NfcTagWriter.kt`, `NfcWriteController.kt`, `NfcIntents.kt` | Android NFC I/O and foreground dispatch | No |
| `reveal/RevealScreen.kt` (`RevealScreen` clock + stateless `RevealStage`), `effects/`, `RevealTemplateGlyph.kt`, `RevealRuntime.kt` | Compose renderer, the six Canvas primitives, picker thumbnails, launcher, quality policy, haptics, trace | No |
| `ui/theme`, `ui/components`, `ui/screens`, `navigation/` | Design system and screens | No |

The Android-free layers are what the JVM unit tests cover.

## Activities

- **`MainActivity`** (`singleTop`) — Compose root (`KipatApp`). Owns an `NfcWriteController`;
  `onNewIntent` gives an armed write first refusal on tag intents, otherwise handles navigation
  extras. If Android ever delivers a Kipat card tap here (AAR fallback), it forwards to the Reveal.
- **`TapExperienceActivity`** (`singleTop`, `taskAffinity=""`, `excludeFromRecents`,
  translucent theme) — the only Reveal host. Resolves its payload from (a) nonce-protected
  in-app extras, or (b) the launch URI via `NfcIntentParser` + `RevealResolver`. Finishes on
  `onStop` so an interrupted Reveal never lingers.

## Navigation

Three tabs via Navigation Compose string routes (`home`, `connections`, `profile`) plus pushed
routes `share`, `person/{connectionId}`, `profile/edit`, `profile/reveal-style` and
`profile/developer` (see `design/research/navigation-decision.md`). Tab switches use `popUpTo(start){saveState}` + `restoreState` +
`launchSingleTop`, so each tab keeps its scroll/search state; re-tapping the active tab pops to its
root; Back from any tab returns to Home, then exits. The navigation bar shows on tab roots only.
Edit profile asks before discarding unsaved changes.

## State

`KipatRepository` exposes `StateFlow`s (identity, card, entitlement, connections, recent taps,
Reveal timings). Screens collect with `collectAsStateWithLifecycle`. Only Edit profile uses a
`ViewModel` (the editable draft must survive configuration changes). Everything is in memory —
persistence (Room/DataStore) and a backend are the planned replacements behind the same object.

**Taps vs connections:** every NFC/deep-link Reveal records a `RecentTap`; a `Connection` is only
created by the explicit *Connect* action.

## Design system

Quiet Graphite (`design/system/foundation.md`). `ui/theme`: neutral palette + one cobalt accent as
semantic `KipatColors`, `RevealPalette` (graphite scrim, accent per Reveal accent choice, card
following the system theme), type scale (system sans, 400/500), spacing 4–40, radius by role
(8/12/16/20), shell motion. `ui/components`: `IdentityBlock`, `RecipientCard`, `TrustLine`,
`PersonRow`, `ActionRow`, `OptionRow`, buttons, top bar, and `KipatSymbols` (Material Symbols
Outlined, the only icon family). Compose previews live in `ui/preview/`.

## Extension points (not implemented)

- Verification: set `RevealPayload.authenticity = CHECKING` on first frame, update from a backend
  / NTAG 424 SUN check without blocking the first visual.
- Billing: `Entitlement` + `RevealTemplateId.requiresPlus` are modelled; nothing is gated yet.
- Designer-authored Rive/Lottie Reveals: add a template whose renderer swaps the Canvas layer,
  keeping the same `IdentityBlock` for dynamic text.
