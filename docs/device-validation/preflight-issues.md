# Preflight issues (found before hardware was available)

These were reproduced off-device with Robolectric while preparing real-device validation. Each
has a regression test that fails without the fix and passes with it. They still need to be
re-checked on a phone (see the checklist rows named in "Re-check").

| ID | P | Area | What was observed (evidence) | Fix | Test | Re-check on device |
|---|---|---|---|---|---|---|
| A11Y-1 | P1 | TalkBack | The tap-outside-to-dismiss handler was on the parent of the surface, so its `clickable` merged initials, name, role and trust into **one "Dismiss" node** (semantics dump: `text=SR, Sam Reyes, Designer … click=Dismiss`). TalkBack would announce the identity as a button and double-tap would close the Reveal. | Dismiss moved to a sibling layer behind the surface, hidden from accessibility; Close and Back remain the accessible dismissals. | `RevealPreflightTest.talkBackSeesIdentityInReadingOrder` | Accessibility: TalkBack order |
| A11Y-2 | P2 | TalkBack | The monogram announced the initials and repeated the name ("SR", "Sam Reyes", then the name heading again). | Monogram is decorative; photos/logos keep "Photo of" / "Logo of". | same test | Accessibility |
| FONT-2 | P2 | Large font | At 2.0× on 320 dp the "Sample data" chip kept its width and squeezed "Card revoked" into a ~62 dp column, three lines tall (node bounds 124 × 211 px). | Trust words and chip are a flow row: the chip wraps below when the row is too narrow. | `RevealPreflightTest.actionsReachableAndTrustReadableAtLargestFontOnSmallPhone` | Accessibility: font 2.0 |
| TRIGGER-1 | P2 | Repeated trigger | Two identical deep links 300 ms apart recorded **two** recent taps for one card and restarted the Reveal; only NFC was debounced. | `TapDebouncer`: any external tap (NFC or link) of the same card within 1.5 s is one tap; Preview/Replay always run. | `TapDebouncerTest` | Lifecycle: same card twice |

Checked and not an issue (off-device): at 1.15–1.5× font on 320–360 dp nothing overlaps and the
actions stay on screen; at 2.0× on 320 × 640 dp the surface scrolls (~118 dp) and the actions are
reachable (FONT-1, same test). Decorative Canvas primitives expose no accessibility nodes.
