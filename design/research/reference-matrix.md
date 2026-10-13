# Reference matrix

Research date 2026-10-13. Sources are official product pages, changelogs, help centres, Play
listings and developer.android.com. Limitations: Play screenshots carry no captions, so
competitor hierarchy is inferred from official help/marketing descriptions; m3.material.io is
JS-rendered, so M3 statements marked *(M3 summary)* come from official-domain search summaries.
Each row is an observation Kipat can act on, not a mood board.

## Linear

### L1 — 2026 UI refresh: chrome recedes
- **SOURCE** [Behind the latest design refresh](https://linear.app/now/behind-the-latest-design-refresh), [changelog 2026-03-12](https://linear.app/changelog/2026-03-12-ui-refresh)
- **USER TRYING TO** work in the content without the frame competing.
- **UX WORKS BECAUSE** sidebar dimmed, smaller icons, compact tabs; "Don't compete for attention you haven't earned."
- **HIERARCHY WORKS BECAUSE** the content area is the only full-contrast region.
- **INTERACTION PRINCIPLE** navigation stays in predictable places; actions standardised in headers.
- **VISUAL PRINCIPLE** "Structure should be felt not seen" — fewer, softer separators.
- **KIPAT LEARNS** the identity is the content; bars, nav and section labels must be quieter than it.
- **MUST NOT COPY** the desktop sidebar density or Linear's product vocabulary.

### L2 — Warmer neutral palette with few variables
- **SOURCE** same refresh post; [How we redesigned the Linear UI](https://linear.app/now/how-we-redesigned-the-linear-ui)
- **USER TRYING TO** read for hours without fatigue.
- **UX WORKS BECAUSE** themes are derived from base/accent/contrast (LCH) instead of ~98 variables; "a more neutral and timeless appearance"; moved from blue-leaning to warmer gray "less saturated but still crisp" (too warm → muddy).
- **HIERARCHY WORKS BECAUSE** text and icons gained contrast while chrome lost colour.
- **INTERACTION PRINCIPLE** contrast is an accessibility setting, not a style.
- **VISUAL PRINCIPLE** neutral first; one accent.
- **KIPAT LEARNS** a small token set (bg, surface, 3 text levels, accent, 3 status) with measured contrast; slightly warm-neutral, not cream.
- **MUST NOT COPY** Linear's purple accent or Inter Display pairing.

### L3 — Mobile redesign: bottom toolbar + create on every screen
- **SOURCE** [Mobile app redesign changelog 2025-10-16](https://linear.app/changelog/2025-10-16-mobile-app-redesign)
- **USER TRYING TO** do core workflows away from the keyboard.
- **UX WORKS BECAUSE** "a new bottom toolbar for quick access to core workflows" and the primary verb ("Create Issue") is always reachable.
- **HIERARCHY WORKS BECAUSE** one verb is promoted above browsing.
- **INTERACTION PRINCIPLE** the product's primary verb is globally available.
- **VISUAL PRINCIPLE** depth used to separate chrome from content (frosted material).
- **KIPAT LEARNS** Kipat's verb is **Share**; it should be one tap from Home, not buried.
- **MUST NOT COPY** the frosted-glass material in the shell — Kipat reserves translucency for the Reveal.

## Raycast

### R1 — Primary action on Enter, everything else in the Action Panel
- **SOURCE** [Raycast Manual: Action Panel](https://manual.raycast.com/action-panel); [API: ActionPanel](https://developers.raycast.com/api-reference/user-interface/action-panel)
- **USER TRYING TO** act on the selected item instantly.
- **UX WORKS BECAUSE** "the action that runs when you press ↵ without opening the panel"; secondary actions are grouped in sections behind ⌘K.
- **HIERARCHY WORKS BECAUSE** one action is visible; the rest are discoverable, not displayed.
- **INTERACTION PRINCIPLE** default action on tap; contextual actions in an overflow/long-press menu.
- **VISUAL PRINCIPLE** rows stay clean because actions aren't drawn on them.
- **KIPAT LEARNS** Connections row tap = open person; Pin/Remove live in long-press/overflow — no per-row buttons, no action sheet on every tap.
- **MUST NOT COPY** keyboard-centric affordances.

### R2 — iOS Home "designed for speed"
- **SOURCE** [Raycast for iOS](https://raycast.com/blog/raycast-for-ios)
- **USER TRYING TO** reach their most-used thing from launch.
- **UX WORKS BECAUSE** Home is the starting point with favourites and the core verb (Ask AI) on top; search via pull-down.
- **HIERARCHY WORKS BECAUSE** most-used first, catalogue later.
- **INTERACTION PRINCIPLE** Home is a launcher for the main job, not a report.
- **VISUAL PRINCIPLE** minimal, few elements.
- **KIPAT LEARNS** Home = your identity + Share + the few people you just met. No configuration rows.
- **MUST NOT COPY** AI-chat prominence.

### R3 — New Raycast: material "used in tasteful ways to enhance the functional nature"; speed is a design requirement
- **SOURCE** [The New Raycast](https://raycast.com/blog/the-new-raycast)
- **USER TRYING TO** get results without waiting.
- **UX WORKS BECAUSE** the team rebuilt the indexer for speed; polish on frequent interactions ("you'll feel within a week").
- **HIERARCHY WORKS BECAUSE** progressive disclosure: Quick AI vs AI Chat share one composer.
- **INTERACTION PRINCIPLE** frequent interactions get the most care.
- **VISUAL PRINCIPLE** special materials only where they serve function.
- **KIPAT LEARNS** spend effects only on the Reveal (the frequent, defining interaction) and make it faster, not more decorated.
- **MUST NOT COPY** Liquid Glass styling.

## Android / Material 3

### A1 — Navigation bar: 3–5 destinations of equal importance; actions aren't destinations
- **SOURCE** [Compose navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar); *(M3 summary)* [M3 navigation bar guidelines](https://m3.material.io/components/navigation-bar/guidelines)
- **USER TRYING TO** move between top-level places.
- **UX WORKS BECAUSE** "Three to five destinations of equal importance", consistent across screens.
- **HIERARCHY WORKS BECAUSE** the bar is stable chrome.
- **INTERACTION PRINCIPLE** a verb (share) is not a place.
- **VISUAL PRINCIPLE** short labels on every item.
- **KIPAT LEARNS** Touch was an action wearing a tab. Use Home / Connections / Profile.
- **MUST NOT COPY** n/a — adopt.

### A2 — Top app bars by need
- **SOURCE** [Compose app bars](https://developer.android.com/develop/ui/compose/components/app-bars)
- **USER TRYING TO** know where they are and what they can do.
- **UX WORKS BECAUSE** small bar for simple screens; medium/large only to "emphasize the headline" as content grows.
- **INTERACTION PRINCIPLE** title + navigation icon + ≤2 actions.
- **VISUAL PRINCIPLE** the bar doesn't need to be large if the content has its own hero.
- **KIPAT LEARNS** replace 28 sp free-floating titles with real top app bars; Home's hero is the identity, so its bar is small.
- **MUST NOT COPY** large collapsing bars on every screen.

### A3 — Lists: leading avatar, 1–3 lines supporting text, trailing metadata
- **SOURCE** *(M3 summary)* [M3 lists guidelines](https://m3.material.io/components/lists/guidelines)
- **USER TRYING TO** scan many people quickly.
- **UX WORKS BECAUSE** trailing slot for "supplemental details such as … date".
- **HIERARCHY WORKS BECAUSE** name > role > time.
- **INTERACTION PRINCIPLE** whole row is the target.
- **VISUAL PRINCIPLE** no per-row containers.
- **KIPAT LEARNS** Connections row = avatar, name, "Role, Organization", trailing "2h"; trust only when abnormal.
- **MUST NOT COPY** n/a — adopt.

### A4 — Edge-to-edge, predictive back, state saving, 48 dp, reduced motion
- **SOURCE** [Edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge); [Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16); [State saving](https://developer.android.com/develop/ui/compose/state-saving); [Accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps); [ValueAnimator](https://developer.android.com/reference/android/animation/ValueAnimator)
- **USER TRYING TO** use the app like every other good Android app.
- **UX WORKS BECAUSE** targetSdk 36+ cannot opt out of edge-to-edge; predictive back is on by default; "preserve user input and navigation-related state"; 48×48 dp targets; animators may be disabled.
- **INTERACTION PRINCIPLE** Back always does the obvious thing; drafts survive rotation.
- **VISUAL PRINCIPLE** content draws under bars with correct insets.
- **KIPAT LEARNS** keep enableEdgeToEdge + inset handling, add a dirty-draft confirm on Back in Edit, keep `areAnimatorsEnabled()` path.
- **MUST NOT COPY** n/a — adopt.

### A5 — Dark theme: system default, tonal surfaces, no hard-coded colours
- **SOURCE** [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3); [Dark theme](https://developer.android.com/develop/ui/views/theming/darktheme)
- **KIPAT LEARNS** "Material 3 represents elevation mainly using tonal color overlays" → graphite surfaces stepped by tone, not shadows or blue-tinted black. Follow system theme by default.
- **MUST NOT COPY** dynamic colour for the brand surfaces — Kipat keeps its own accent so identity looks consistent.

### A6 — Android "Tap to share" (platform contact exchange, Android 17, Pixel 6+)
- **SOURCE** [Google support: Tap to share](https://support.google.com/android/answer/17161144?hl=en)
- **USER TRYING TO** exchange contact cards by touching phones.
- **UX WORKS BECAUSE** built into the OS, chooses which fields to share, can receive only.
- **KIPAT LEARNS** phone-to-phone exchange is a platform feature now; Kipat's distinct value is the **physical card** (works without Kipat on the receiving phone's side via the URL) and the in-context **Reveal**. Do not promote an unbuilt HCE "phone tap" as a headline feature.
- **MUST NOT COPY** n/a.

### A7 — Use the system Sharesheet; don't build your own
- **SOURCE** [Sending simple data](https://developer.android.com/training/sharing/send)
- **KIPAT LEARNS** "Share link…" opens the system sheet with a title preview; Kipat's Share screen adds only what the system can't (QR, card state).

## Blinq

### B1 — NFC accessory pairing; tap opens the browser
- **SOURCE** [Setting up Blinq NFC accessories](https://support.blinq.me/en/articles/77774-setting-up-your-blinq-nfc-accessories)
- **USER TRYING TO** make a physical card share their digital card.
- **UX WORKS BECAUSE** short wizard, free hardware use.
- **BUT** pairing sits in the Main menu; tap = browser page (context switch).
- **KIPAT LEARNS** the card's state belongs on Home as one row; the tap opens an overlay in context when Kipat is installed.
- **MUST NOT COPY** the buried pairing entry.

### B2 — Recipient save on Android = .vcf download (5–6 taps); "Lead Gen Form" share-back
- **SOURCE** [Saving a contact to an Android device](https://support.blinq.me/en/articles/68045-saving-a-contact-to-an-android-device); [Blinq basics: contacts](https://support.blinq.me/en/articles/68029-blinq-basics-contacts)
- **KIPAT LEARNS** "Save contact" must be one tap into Android's own contact editor (ACTION_INSERT). Share-back is "Connect", human language.
- **MUST NOT COPY** "Lead" vocabulary, file downloads.

### B3 — Send menu with ~10 methods; Wallet at the bottom
- **SOURCE** [Add your Blinq card to Google Wallet](https://support.blinq.me/en/articles/68012-add-your-blinq-card-to-google-wallet); [Blinq mobile app](https://blinq.me/solutions/mobile-app)
- **KIPAT LEARNS** Share offers the 3 methods that matter (card, QR, link) + the system sheet. Everything else later.

## HiHello

### H1 — Editor: searchable fields, preview at top, design tabs gated by tier
- **SOURCE** [New edit experience](https://www.hihello.com/blog/feature-launch-new-edit-experience)
- **USER TRYING TO** build and style a card.
- **UX WORKS BECAUSE** search beats a 40+ field list; preview always visible.
- **BUT** premium designs are upsells; explicit Save is easy to miss.
- **KIPAT LEARNS** small curated field set chosen by template; a clear "Save" in the edit bar plus a discard prompt; preview is the Profile screen itself.
- **MUST NOT COPY** drag-and-drop page building.

### H2 — Recipient: Save Contact → import file (≈6 steps on Android); "Join HiHello" prompt
- **SOURCE** [Receiving a HiHello card](https://hihello.com/blog/receiving-a-hihello-card)
- **KIPAT LEARNS** no acquisition prompt on the recipient's first view; identity first, actions second.

### H3 — Contact manager framed for companies (tags, notes, CRM sync, company-owned contacts)
- **SOURCE** [Business Contact Manager](https://www.hihello.com/features/business-contact-manager)
- **KIPAT LEARNS** Connections stays personal: who, what they do, when and how you met. No CRM fields in v1.

## Popl

### P1 — Primary tab is "Leads"; Share offers QR, Link, NFC to the same live profile
- **SOURCE** [Mobile app walkthrough](https://support.popl.co/en/articles/11720865-mobile-app-walkthrough-digital-business-cards); [Play listing "Popl: AI Lead Capture"](https://play.google.com/store/apps/details?id=com.nfc.popl&hl=en_US)
- **KIPAT LEARNS** the three share methods pointing at one URL is right; a sales-first IA is not Kipat.
- **MUST NOT COPY** Leads framing, badge scanner, branding removal as a paid feature.

### P2 — Lead Capture Mode shows a form before the identity
- **SOURCE** [Getting started with Popl](https://support.popl.co/en/articles/11465818-getting-started-with-popl)
- **KIPAT LEARNS** the strongest anti-pattern found: the recipient's first sight must be the person, never a form.

## Synthesis

1. **Identity first, everywhere** (L1, R2, P2, H2).
2. **One verb per context; the product verb is Share** (L3, R1, A1).
3. **Quiet chrome, tonal neutral surfaces, one accent, measured contrast** (L1, L2, A5).
4. **Lists, not cards, for people** (A3, R1, H3).
5. **Native beats web for the recipient** — overlay Reveal and one-tap Save contact are Kipat's concrete advantages (B1, B2, H2).
6. **Don't headline unbuilt or platform-duplicated features** (A6).
7. **Effects only where they carry meaning and speed matters** (R3).
