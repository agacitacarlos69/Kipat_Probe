# Architecture

Single `:app` module, Kotlin + Jetpack Compose, no DI framework (one in-memory repository is
the only shared dependency).

## Layers

| Package | Responsibility | Android-free? |
|---|---|---|
| `model/` | Identity, profile fields/templates, connections, card, taps, authenticity + its presentation, profile validation | Yes |
| `reveal/RevealTemplate.kt`, `RevealEngine.kt`, `RevealPayload.kt`, `RevealStyleDraft.kt` | Six-template family and its `RevealChoreography`, `RevealTimeline`, Kipat motion curves, frame math, payload + Intent codec, picker draft | Yes |
| `nfc/NfcIntentParser.kt`, `NfcStatus.kt` | Kipat URI parsing, tag URI builder, write states | Yes |
| `data/` | `SampleData`, `KipatRepository` (StateFlows, canonical identity + `profileRevision`), `KipatIdentityStore` (atomic persistence, public snapshot), `PublicKipatProfileSnapshot`, `RevealResolver` (legacy tap → payload) | Mostly (the store needs a Context) |
| `cards/` | Linked cards: `LinkedKipatCard`, `KipatCardId`, `KipatCardResolver` + `LocalPrototypeCardResolver`, `LinkedCardReveal`, `CardLinkPlan`; `KipatCards` registry and `CardLinkSession` (reader mode) | Model and resolver yes; registry and session no |
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
routes `share`, `person/{connectionId}`, `profile/edit`, `profile/reveal-style`, `profile/cards`,
`profile/cards/add`, `profile/card/{cardId}` and `profile/developer` (see `design/research/navigation-decision.md`). Tab switches use `popUpTo(start){saveState}` + `restoreState` +
`launchSingleTop`, so each tab keeps its scroll/search state; re-tapping the active tab pops to its
root; Back from any tab returns to Home, then exits. The navigation bar shows on tab roots only.
Edit profile asks before discarding unsaved changes.

## One identity, many transports

```
KipatRepository.identity (+ profileRevision) ──persist──► kipat_identity.json (KipatIdentityStore)
        │
        ▼
PublicKipatProfileSnapshot ──► HCE Kipat Tap (KipatTapStore.snapshot)
        │                 ──► linked card: kc_ id → KipatCardResolver → owner snapshot
        │                 ──► preview, QR, link
        ▼
NfcProfileReveal.toRevealPayload → RevealPayload → RevealScreen
```

Presentation (Reveal template, accent) belongs to the identity, never to a card or a phone. Every
edit goes through `KipatRepository.publishIdentity`. It persists the change, then raises the
revision by one, so the next tap of the phone or of any linked card serves it. This is local parity
on one phone; a backend resolver is the documented next step
([nfc-card-identity-sync.md](nfc-card-identity-sync.md)).

## State

`KipatRepository` exposes `StateFlow`s: identity, profile revision, card, entitlement, connections,
recent taps and Reveal timings. Screens collect them with `collectAsStateWithLifecycle`. Only Edit
profile uses a `ViewModel`, because the editable draft must survive configuration changes.

**Persistence is local-first and needs no backend.**
- **Identity:** atomic JSON (`KipatIdentityStore`).
- **Linked cards:** atomic JSON (`KipatCards`).
- **Connections and recent taps:** Room, in `kipat.db` (`KipatLocalStore`, schema in `app/schemas/`).
  - The repository stays the synchronous source the UI and the Reveal read.
  - At process start, the store loads the rows into it before any screen, card tap or Kipat Tap
    delivery reads them.
  - Every change is then written through on one ordered background thread.
- **Sample data:** never written to disk. Debug builds show it in memory next to real rows; release
  builds start empty.

**Taps vs connections** (`ConnectionRules`):
- Every NFC or link Reveal records a `RecentTap`. A `Connection` is created only by the explicit
  *Connect* action.
- There is one Connection per public identity id, so a phone tap, a linked card and a link all reach
  the same row.
- A later encounter refreshes `lastSeenAt`. Only a live profile (their phone, their linked card) that
  is not older than the stored one refreshes the public snapshot. The local id, `connectedAt`, the
  pin and the source are never changed by a refresh.
- A removed Connection is never recreated by an encounter.

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
