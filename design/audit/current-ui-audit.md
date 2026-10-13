# Current UI audit (pre-redesign)

Audited build: `a06d3aa` on `claude/magical-curie-ex2ev1`. Evidence: the Compose source under
`app/src/main/java/com/kipat/probe/ui` and `reveal/`, plus Robolectric renders in
`docs/images/app-screens.png` and `docs/images/reveal-strip.png`. Contrast ratios were measured
with the WCAG 2.x formula. Nothing here is protected because a previous run made it.

## Summary verdict

The engineering is sound; the visual layer is the generic "premium dark AI app": a cyan + violet
accent pair on a blue-tinted near-black, glass cards with gradient hairlines, gradient avatar
rings, pill buttons, tracked ALL-CAPS eyebrows, `A · B` meta strings on almost every line, and a
glowing particle Reveal. Information architecture has a bigger problem than the paint: **the
product's main job — sharing your identity — is the last item on Home and the Touch tab is mostly
a developer tool.**

---

## Home

| | |
|---|---|
| PURPOSE | Show the identity I'm about to share and let me share it |
| PRIMARY USER JOB | Share / present my Kipat |
| CURRENT PRIMARY ACTION | **Preview Reveal** (cyan pill inside the hero card) — a self-preview, not sharing |
| CURRENT SECONDARY ACTIONS | Edit (pill), See all, Share My Kipat (outlined pill at the very bottom of the scroll), row taps |
| CURRENT HIERARCHY | Greeting → giant hero card → "YOUR KIPAT" group (card, Reveal, template, plan) → avatar strip → recent taps → Share |
| UNNECESSARY CONTENT | "Good morning, Vash" (repeats the name shown 100 dp below); duplicate avatar in the top bar; "Current Reveal" and "Profile template" rows (configuration that belongs to Profile); "Plan · Kipat Free · Plus coming soon" (upsell placeholder with no function) |
| UNNECESSARY CONTAINERS | Hero identity is a 28 dp-radius glass card with gradient border holding a nested status pill and two pill buttons (card-in-card visual); grouped container around four unrelated rows |
| GENERIC AI PATTERNS | Greeting dashboard; tracked ALL-CAPS eyebrows ("ACTIVE IDENTITY", "YOUR KIPAT", "RECENT CONNECTIONS"); violet gradient wash behind the top of the screen; gradient-ring avatars; `Verified · demo`, `Signature · Drip`, `Active · demo` |
| VISUAL NOISE | Three status pills in the first screen; cyan + violet + mint used simultaneously; Kipat mark icon repeated in top bar, eyebrow and card row |
| NAVIGATION ISSUES | Card row jumps to the Touch tab (a developer page); template rows jump to Profile — rows navigate across tabs, which breaks place |
| COPY ISSUES | "Preview Reveal" is internal vocabulary; "Share My Kipat" title-case; "Plus coming soon" is marketing in product UI; "Not saved · …" prefixes |
| ACCESSIBILITY | Tertiary text #6B7489 on #0B0E15 = **4.12:1** (fails AA for body); horizontal avatar strip shows first names only (ambiguous for TalkBack and sighted users); Recent taps rows announce "Open X" but navigate to a list, not X |
| KEEP | Identity as the first meaningful content; recent people; card status (one line); the Share action |
| CHANGE | Make **Share** the primary action; identity shown as typography on the page (no hero card); card status as one list row |
| REMOVE | Greeting, plan row, template/Reveal rows, gradient wash, eyebrows, avatar strip (replace with a short list) |

## Connections

| | |
|---|---|
| PURPOSE | Find and open a person I met |
| PRIMARY USER JOB | Search / scan → open a person |
| CURRENT PRIMARY ACTION | Tap row → **bottom sheet** → "Open Profile" (two steps to reach a person) |
| CURRENT SECONDARY ACTIONS | Sort segmented control, pin/unpin, replay, remove (all in the sheet) |
| CURRENT HIERARCHY | 28 sp title → pill search → "6 connections" + segmented control → PINNED / RECENT eyebrows → rows |
| UNNECESSARY CONTENT | Authenticity text on **every** row ("Verified · demo" ×4) — trust shown loudest where it matters least; connection count |
| UNNECESSARY CONTAINERS | Bottom sheet for every tap (excessive sheets) |
| GENERIC AI PATTERNS | Pill search, violet segmented selection, gradient-ring avatars, middle-dot detail lines, filled star glyph |
| VISUAL NOISE | Colored trailing labels (mint/amber/rose) on every row compete with names |
| NAVIGATION ISSUES | The sheet duplicates the profile screen's actions; Back from profile returns to the list (fine) but the sheet adds a modal layer for the most frequent action |
| COPY ISSUES | "Remove Connection" title case; "Pin to top"; "Connection source" as an action that does nothing |
| ACCESSIBILITY | Pinned state = star icon with contentDescription only on the icon; sheet action "Connection source" looks tappable but isn't |
| KEEP | Search, Recent/A–Z sorting, pinned group, undo on remove, replay Reveal |
| CHANGE | Row tap opens the person directly; sort becomes a quiet menu; trust shown only when it is *not* normal (unverified/revoked) plus a small verified mark |
| REMOVE | Bottom sheet, segmented control, per-row trust labels, star glyph |

## Touch

| | |
|---|---|
| PURPOSE (as built) | Share by phone tap + prototype NTAG writer + diagnostics |
| PRIMARY USER JOB | None that works yet for consumers: phone-to-phone (HCE) is not implemented |
| CURRENT PRIMARY ACTION | **Arm Tag Write** — a developer action is the only enabled primary button |
| CURRENT SECONDARY ACTIONS | Name-hint switch, cancel, diagnostics toggle, debug Reveal triggers |
| CURRENT HIERARCHY | Animated concentric "beacon" → "Ready to share" → My Kipat group → Prototype Card Tools → Diagnostics |
| UNNECESSARY CONTENT | "Ready to share / Hold another supported device nearby" claims a capability that doesn't exist; My Kipat group duplicates Home |
| UNNECESSARY CONTAINERS | Group-in-group (prototype tools inside a container with nested status pill and switch row) |
| GENERIC AI PATTERNS | Ambient pulsing rings (decorative motion outside the Reveal), monospace URI, orange "Developer / prototype" label |
| VISUAL NOISE | Beacon animation runs constantly while the screen is open |
| NAVIGATION ISSUES | A whole top-level tab for a placeholder + dev tools; sharing by **QR or link — what most recipients actually need — is absent** |
| COPY ISSUES | "Arm Tag Write", "Ready to share" (untrue) |
| ACCESSIBILITY | Beacon has a contentDescription that repeats the headline; ambient animation stops only when system animations are off |
| KEEP | All writer behaviour, diagnostics, debug triggers, legacy probe log |
| CHANGE | Move consumer sharing (QR, link, card) to a **Share** screen reached from Home; move writer + diagnostics to **Profile → Developer tools** |
| REMOVE | The Touch tab, the beacon, the false "Ready to share" |

## Profile

| | |
|---|---|
| PURPOSE | See how others see me; edit my identity |
| PRIMARY USER JOB | Edit fields; preview |
| CURRENT PRIMARY ACTION | Ambiguous: "Preview Reveal" (in the hero) vs "Publish locally" (at the bottom, below ten template tiles) |
| CURRENT SECONDARY ACTIONS | Identity-type chips, template tiles, Reveal tiles, theme chips, discard |
| CURRENT HIERARCHY | Status pill → hero card (duplicate of Home) → type chips → 7 template tiles → 10 Reveal tiles → 4 theme chips → fields → publish |
| UNNECESSARY CONTENT | Ten Reveal tiles with glowing thumbnails ("Full"/"Preview"/"Plus" tags) before the user can edit their own name |
| UNNECESSARY CONTAINERS | Hero card, tile cards with nested thumbnail boxes |
| GENERIC AI PATTERNS | Feature tiles carousel; eyebrow "STUDENT · STUDENT MINIMAL"; pill buttons |
| VISUAL NOISE | Horizontal carousels × 2 plus chip rows × 2 |
| NAVIGATION ISSUES | **View and edit are the same screen** — recipient-facing content and editing chrome are mixed |
| COPY ISSUES | "Publish locally", "Approved fields", "Kipat profiles are built from these fields only — no custom code or markup" (engineering rationale shown to users) |
| ACCESSIBILITY | Template tiles use `selectable` (good) but `minLines=2` descriptions truncate at large font scales |
| KEEP | Controlled fields + validation, template model, Reveal style choice, local publish semantics |
| CHANGE | Split into **Profile (view)** and **Edit profile** (full-screen, Save/close); Reveal style and template become rows that open simple choosers |
| REMOVE | Carousels, hero card, tags, rationale copy |

## Reveal (TapExperienceActivity)

| | |
|---|---|
| PURPOSE | Over another app: tell me who this is, whether it's genuine, let me connect/save |
| PRIMARY USER JOB | Understand → Connect (or Save contact) |
| CURRENT PRIMARY ACTION | Connect (cyan pill), secondary "Open in Kipat" |
| CURRENT HIERARCHY | Source label → close → glow + 2 ripples + drip bead + 44 particles + outline trace → glass card (eyebrow "KIPAT", gradient avatar ring, name at **900 ms**, role at 1200 ms, badge at 1500 ms, CTAs at 1800 ms) → "Tap outside to dismiss" |
| UNNECESSARY CONTENT | "KIPAT" eyebrow; source string "Kipat · debug link · demo data"; "Tap outside to dismiss" hint |
| GENERIC AI PATTERNS | Additive cyan/violet glow, converging particles, drip bead, gradient card border — the "energy/hologram" vocabulary the brief rejects |
| VISUAL NOISE | Five simultaneous effect layers in the first 600 ms; nothing readable until 900 ms |
| COMPREHENSION | Identity is the job but arrives last; particles communicate nothing about *who* |
| MISSING | **Save contact** (the recipient's most common need when they don't use Kipat) |
| ACCESSIBILITY | Reduced-motion path is good (fade only); scrim + translucent card over a bright app can reduce card text contrast |
| KEEP | Own task over previous app, dynamic payload, nonce, progressive scrim, perimeter trace idea, haptics, timing markers, reduced-motion path, finishing on stop |
| CHANGE | Opaque, calm card; identity readable by ~900 ms total with name earlier; one contact ripple + a precise trace; Connect + Save contact + Open profile |
| REMOVE | Particles, drip, additive glow, gradient borders, eyebrow, hint text |

---

## AI-slop checklist (every occurrence and why it exists)

| Pattern | Where | Why does it exist? | Verdict |
|---|---|---|---|
| Card inside card | Home hero (card → status pill + button row); Touch prototype group (group → pill + switch row); Profile tiles (tile → thumbnail box) | Habit of wrapping everything | **Remove** — typography + spacing suffice |
| Card used to group two lines | Home "Your Kipat" group; Touch "My Kipat" group | None | **Remove** → list rows |
| Excessive radius | 28 dp cards, CircleShape buttons, pill search, pill segmented | "Premium" default | **Redesign** — 8/12/16 family |
| Too many equally prominent surfaces | Home: hero, group, strip, group, button | No priority decided | **Redesign** — one hero (identity), rest is list |
| Glowing borders | Identity card gradient border; Reveal card | Decoration | **Remove** |
| Unnecessary gradients | Home backdrop wash; avatar rings; card fill | Decoration | **Remove** |
| Purple/cyan AI palette | Global `Signal`/`Halo` | Default | **Replace** with neutral + one ink accent |
| Giant hero card | Home, Profile | Default dashboard template | **Remove** |
| "Good afternoon, Vash" | Home | Filler | **Remove** |
| Feature tiles | Profile template/Reveal carousels | Showing the catalog | **Replace** with chooser screens |
| Fake dashboard metrics | "6 connections" count | Filler | **Remove** |
| Random status pills | Home (3), Touch, Profile, rows | Status everywhere | **Reduce** to trust-relevant only, as icon + text |
| Random decorative icons | Mark icon in eyebrows and card rows | Branding reflex | **Reduce** to top bar + Reveal |
| Huge empty spaces | Reveal card reserves CTA space while empty for 1.8 s | Layout stability | **Keep stability, shorten timeline** |
| Overuse of glass | Identity card, Reveal card | "Premium" | **Remove** from shell; Reveal card becomes opaque |
| Decorative particles outside Reveal | Profile Reveal thumbnails; Touch beacon | Decoration | **Remove** |
| Generic "premium" black | Blue-tinted near-black #0B0E15 | Default | **Replace** with neutral graphite per Android dark guidance |
| Huge titles, little content | 28 sp "Connections"/"Touch"/"Profile" | Template | **Use M3 top app bar sizes** |
| Every action as a button | Edit, See all, Share, Preview all pills | No hierarchy | **One primary per screen**; others as text/rows |
| Weak list hierarchy | Trailing colored trust label outweighs the name | No hierarchy | **Fix** |
| Inconsistent icons | Filled Material + outlined Material + custom stroke icons (Touch/Connections/Mark) | Mixed sources | **One family**: Material Symbols Outlined |
| Repeated CTA labels | "Preview Reveal" on Home and Profile; "Replay Reveal" in sheet and profile | Duplication | **Consolidate** |
| Accidental iOS patterns | Segmented control as primary sort; centered-card modal Reveal with "Tap outside" | Habit | **Replace** with Android menu patterns |
| Excessive bottom sheets | Connection sheet | Shortcut | **Remove** |
| Unnecessary floating buttons | None | — | OK |
| Uppercase everywhere / tracked labels | All section headers | Template chrome (frontend-design tell #5) | **Sentence case** |
| Middle-dot meta strings | ~15 places | Template chrome (tell #5) | **Two lines or plain words** |
| Monospace data labels | Touch URI, timings | Template chrome (tell #5) | Keep **only** in developer tools |

## Contrast measurements (WCAG 2.x)

| Pair | Ratio | Result |
|---|---|---|
| Dark tertiary #6B7489 on #0B0E15 | 4.12 | Fails AA body |
| Dark tertiary on surfaceContainer #10141D | 3.93 | Fails AA body |
| Light tertiary #6B7489 on #F5F6FA | 4.34 | Fails AA body |
| Light accent #0B8496 on #F5F6FA | 4.09 | Fails AA body (used for link text "See all") |
| White on light accent #0B8496 | 4.42 | Fails AA body (button labels) |
| Dark secondary #98A1B5 on #0B0E15 | 7.45 | Pass |
