# Feature gates

Every proposed feature passes this gate **before design starts**, and the answers are written into
its spec. A feature that fails the gate is not "deferred". It is declined until something changes.

## The six questions

| # | Question | Pass looks like | Fail looks like |
|---|---|---|---|
| 1 | **Does it strengthen identity?** | The person is presented more accurately or more personally | It adds fields, links or widgets that compete with the name |
| 2 | **Does it strengthen the tap?** | Taps succeed more often, faster, or with a clearer result | It adds a step before or during the tap |
| 3 | **Does it strengthen Connections?** | It helps you remember or reach someone you chose | It adds activity, counts, or reasons to scroll |
| 4 | **Does it make repeated use meaningfully better?** | The tenth use is better than the first | It is a novelty that wears off after one use |
| 5 | **Can it live after the Reveal instead of before it?** | Yes: it's an action after identity, or on a Person | It needs to appear before or alongside the name |
| 6 | **Would removing it make Kipat clearer?** | No, removing it would lose a job people have | Yes, then remove it |

Rule: a feature needs **at least two of 1–4**, **a yes to 5**, and **a no to 6**. A feature that
duplicates a platform capability (contact fields, generic file transfer) must also show what
Kipat's identity context adds that the platform doesn't.

### Worked examples

| Feature | 1 | 2 | 3 | 4 | 5 | 6 | Result |
|---|---|---|---|---|---|---|---|
| Connections persistence | – | – | ✓ | ✓ | ✓ | No | **Pass** |
| Event label after Connect ("met at") | – | – | ✓ | ✓ | ✓ | No | **Pass** |
| Backend card resolver + web profile | ✓ | ✓ | ✓ | ✓ | ✓ | No | **Pass** |
| Seventh Reveal template | ✓ (weak) | – | – | – | ✓ | Yes, it dilutes recognition | **Fail** |
| "People nearby" discovery list | – | – | – | – | No, it replaces the tap | Yes | **Fail** |
| Six share icons on the Reveal | – | – | – | – | No | Yes | **Fail** |
| Send file after Connect | – | – | ✓ (history) | ? | ✓ | ? | **Conditional**, see below |

---

## Kipat Share decision framework

Working name: Kipat Share. File, photo, link or contact sent to someone you just met, after the
Reveal. The platform already offers this: Android's Tap to share sends files by touching phones on
Pixel 6+ with Android 17, with receiver acceptance
([Google Help](https://support.google.com/android/answer/17161144?hl=en),
[fonearena](https://www.fonearena.com/blog/?p=489498)). Kipat Share is therefore **not a
differentiator by itself.** It may only proceed when **all seven** conditions hold. Thresholds are
set **now, before testing**, and are not results.

| # | Condition | Acceptance criterion (measurable, non-vanity) | Evidence location |
|---|---|---|---|
| 1 | **Kipat Tap is physically reliable** | On **≥ 4 OEM pairs** (including Samsung, Xiaomi or OPPO, and Pixel), in the ambient scenario (Kipat not open, another app in front): **≥ 9 of 10** touches produce both Reveals with no system tag viewer, and each pair is recorded | `docs/device-validation/oem-matrix.md` rows filled from devices |
| 2 | **The card story is polished** | The card resolves on a phone **other than the owner's** (backend resolver live), and a non-Kipat phone shows a web profile with Save contact. Card linking succeeds ≥ 9/10 on NTAG213/215/216 on ≥ 2 OEMs | Linked-card checklist per device |
| 3 | **The Reveal is recognisable** | In an unprompted test with **≥ 10 people** who saw one Reveal, at least **7** describe it as "it showed who the person is" (or similar) without mentioning a technology or effect | Test notes, verbatim answers |
| 4 | **Connections has clear value** | Connections persist across process death, and **≥ 10 participants** over **≥ 2 weeks** each use a Person for a real task (find, call, message, Save contact) at least once without being asked to | Diary-study notes |
| 5 | **Identity-first UX has been validated** | In a moderated test of the chosen wireframe (≥ 5 people), **no participant** opens a share action before reading the name, and **every participant** can dismiss without sharing | Test notes |
| 6 | **Privacy is preserved** | The threat model (`nearby-sharing-technical-options.md`) is reviewed. Zero identifiers beyond the public profile are sent (checked by packet/log inspection). Nothing is advertised outside a session. Every item requires Accept. No new location or broad storage permission | Review record + test logs |
| 7 | **Transfer performance is competitive** | For a **25 MB** file on the same phone pair, median time from the sender tapping Send to the file being saved is **≤ 1.5×** Quick Share's on that pair, and **≥ 19 of 20** transfers succeed, including after separation and retry | Measurement sheet per pair |

**Explicitly excluded as criteria:** downloads, daily actives, number of shares, "engagement",
time-in-app, number of Connections. These measure attention, not whether sharing makes Kipat better.

### Staged scope once the gate opens

| Stage | Scope | Why this order |
|---|---|---|
| S0 (allowed any time, no new transport) | From a Person: **Share link** / **Share contact** via the system Sharesheet | Zero new permissions; it tests demand |
| S1 | **Send one file or photo** after Connect, with NFC-bootstrapped point-to-point transport and the consent sheet | Highest value, highest risk; one item only |
| S2 | Share history on the Person ("Ana sent design.pdf, 12 Oct") | Strengthens Connections (gate question 3) |
| Never | Multi-select, folders, auto-accept, receive-from-anyone, sharing before the Reveal | Breaks consent or identity-first |
