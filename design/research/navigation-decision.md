# Navigation decision

**Before:** Home · Connections · Touch · Profile (4 tabs)
**After:** Home · Connections · Profile (3 tabs) + **Share** as Home's primary action (full screen,
reachable from Home and from Profile) + **Developer tools** under Profile.

## User jobs (by frequency)

| Job | Frequency | Where it should live |
|---|---|---|
| Present / share my identity (card, QR, link) | Every networking moment | One tap from launch → Home primary action |
| Find / open someone I met | Often, after events | Destination: Connections |
| Act on a fresh tap (connect, save) | Right after a tap | The Reveal itself; then Home "Recent" |
| Edit my identity, choose Reveal style | Occasionally | Destination: Profile → Edit |
| Check the physical card works | Occasionally | Home row (one line) |
| Write a prototype tag, diagnostics | Developers only | Profile → Developer tools |

## Does Touch earn a tab?

| Test | Touch | Evidence |
|---|---|---|
| Is it a *place* users return to and browse? | No — it's a verb (share) plus a dev tool | M3: nav bar is for "destinations of equal importance" (A1) |
| Is its consumer content functional today? | No — phone-to-phone (HCE) isn't built; "Ready to share" was untrue | Audit |
| Does the platform already cover it? | Partly — Android 17 "Tap to share" does phone-to-phone contact exchange on Pixel 6+ | A6 |
| What do recipients without Kipat actually need? | QR and link — missing from Touch | Competitors all lead with QR/link (B3, P1) |
| Is the verb better placed on Home? | Yes — Linear's mobile redesign puts the primary verb on every screen (L3); Raycast Home leads with the core action (R2) | |

**Touch fails every test.** Its contents split cleanly: consumer sharing → a **Share** screen
(QR, copy link, system share, card status, honest "phone tap — later"); developer content →
**Profile → Developer tools** (unchanged behaviour: armed writer, diagnostics, debug triggers,
legacy probe log).

## Option A vs B

| | A: Home / Connections / Profile + Share action | B: Home / Connections / Touch / Profile |
|---|---|---|
| Task speed to share | 1 tap (Home → Share) | 1 tap (Touch) but lands on a dev page |
| Honesty | Share screen shows only working methods | Tab promises a capability that isn't built |
| M3 fit | 3 destinations, 1 verb | 4 tabs, one of which is an action |
| Dev tools risk | Demoted under Profile | In the consumer nav |
| Symmetry | 3 tabs | 4 tabs |

**Choose A.** Two tabs was considered (Home + Connections, Profile pushed from Home) and rejected:
M3 recommends tabs, not a navigation bar, below three destinations, and Profile editing is a
place users return to.

## Details

- Selected tab icons use the filled Material Symbol; unselected use outlined (M3 convention).
- Re-tapping the active tab pops to its root; Back from a non-Home tab returns Home, then exits.
- Share is a full-screen route (not a bottom sheet) so the QR is large and Back is predictable.
- The NFC writer still only arms its foreground dispatch while the Developer tools screen is open.
- The flexible M3 navigation bar is the current recommendation *(M3 summary)*; the Compose
  `NavigationBar` with short labels gives the same 3-item layout on compact width.
