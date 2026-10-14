# Kipat Tap — ambient two-phone checklist

**Primary acceptance test.** Two physical phones, same build, different profiles, Kipat Tap
enabled once on each, NFC on, both unlocked, **Kipat not open on either**. Nobody presses
Present, Receive or anything else after the touch. Write PASS / FAIL / NOT TESTED plus the log
file. Simulations and emulators are not evidence.

Build: `____` · Date: `____`

| | Phone A | Phone B |
|---|---|---|
| Manufacturer / model | | |
| Android version / OEM skin | | |
| Kipat Tap (Developer tools → Kipat Tap (ambient) → Availability) | | |
| NFC dispatch alias shown | | |

## Scenarios (record which phone became initiator: its log has `ambient_initiator_created`)

| Scenario | A shows B's Reveal | B shows A's Reveal | Initiator | "New tag collected"? | Dismiss returns to | Notes / logs |
|---|---|---|---|---|---|---|
| A browser, B Settings (`apps`) | | | | | A: browser / B: Settings | |
| Both on home screen (`home`) | | | | | launcher / launcher | |
| A YouTube, B Chrome | | | | | | |
| A Messenger, B Settings | | | | | | |
| A Kipat Home, B another app | | | | | | |
| Process killed with `am kill` (`killed`) | | | | | | |
| Swap physical orientation | | | | | | |
| Bring A toward B / B toward A | | | | | | |
| Touch at the same moment | | | | | | |

PASS for a scenario only if **both** phones show the other person's Reveal with no user
action after the touch, and dismissing returns each to the app it was showing.

## Expected per phone

| Check | A | B |
|---|---|---|
| One subtle haptic on recognition (not per packet) | | |
| Reveal shows name, headline, organisation; "Unverified Kipat" + "Nearby Kipat"; never Verified | | |
| Not in Connections until Connect is pressed; appears under Recent | | |
| Connect → listed as "Phone tap" | | |
| Exactly one Reveal (no second one a moment later) | | |
| No Kipat Home left behind the Reveal after dismissing | | |
| Responder log shows `background_launch_allowed` (not `_blocked`) | | |

## Privacy and failure cases

| Check | Result | Notes |
|---|---|---|
| A locked, B unlocked (`locked`): A shares nothing; Android may ask A to unlock and tap again | | |
| After unlocking A, the retry works | | |
| Kipat Tap turned off on A: A shares nothing; B shows no Reveal of A | | |
| Phones pulled apart early: "Keep the phones together", then success or a clean exit, never stuck | | |
| `am force-stop` on both (`forcestop`): record behaviour (Android 17 doesn't dispatch to stopped apps) | | |
| Same profile on both phones: "This is your own Kipat profile", no Reveal | | |
| Physical Kipat card and NFC full profile test card still work (regression) | | |
| Developer tools → Manual NFC peer test still works | | |

## If the generic tag viewer appears
| Question | Answer |
|---|---|
| Which phone showed it? | |
| That phone's log has `ndef_dispatched`? | |
| The other phone's log has `type4_selected` and `bootstrap_read`? | |
| Kipat Tap was enabled on the other phone (it serves no tag otherwise)? | |
| Another app on the other phone also registers `D2760000850101` (Android would ask)? | |
| `raw/dumpsys-nfc-*` saved | |
