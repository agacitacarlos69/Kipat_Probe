# Two-phone NFC checklist

Fill from two **physical** phones running the same build. Write PASS / FAIL / NOT TESTED and a
note; attach the matching `raw/*.log` file name. The JVM protocol simulation is not evidence here.

Build: `____` · Date: `____`

| | Phone A | Phone B |
|---|---|---|
| Manufacturer / model | | |
| Android version / OEM skin | | |
| NFC / HCE (Developer tools → Phone-to-phone NFC) | | |

## Round 1: A presents, B receives (send my profile back ON)
| Check | Result | Notes / log |
|---|---|---|
| A: Present my Kipat → "Ready to share", countdown from 60 s | | |
| B: Receive a Kipat → "Looking for Kipat" | | |
| Touch: Android's "New tag collected / Empty tag" does NOT appear on either phone | | |
| B: "Kipat found" → "Receiving profile" → "Exchanging profiles" | | |
| B: Reveal shows A's name, headline, organisation; "Unverified Kipat" + "Nearby Kipat" | | |
| A: "Kipat found" → "Receiving profile" → Reveal of B | | |
| One subtle vibration on contact and one on completion (not per packet) | | |
| Neither phone lists the other under Connections until Connect is pressed | | |
| Connect on B → A appears in Connections as "Phone tap" | | |
| Developer tools shows role, peer, bytes, integrity Passed, measured duration | | |

## Round 2: one way (B: Send my profile back OFF)
| Check | Result | Notes / log |
|---|---|---|
| B receives A's Reveal | | |
| A shows "Shared", no Reveal of B, no error | | |

## Round 3: roles swapped (B presents, A receives)
| Check | Result | Notes / log |
|---|---|---|
| Same as round 1 with roles reversed | | |

## Edge cases
| Check | Result | Notes |
|---|---|---|
| Phones pulled apart mid-exchange → "Keep the phones together and try again", retry works | | |
| Present expires after 60 s → "Sharing expired", Present again works | | |
| Receive while the other phone is NOT presenting → nothing happens or a clear message | | |
| Presenter screen locked → no exchange (requireDeviceUnlock) | | |
| Same phone profile on both (same account) → "This is your own Kipat profile" | | |
| Touch twice quickly after success → one Reveal, one recent tap | | |
| Physical Kipat card still opens the Reveal (regression) | | |
| NFC full profile test card still reads/writes (regression) | | |

## If "New tag collected / Empty tag" appears
| Question | Answer |
|---|---|
| Which phone showed it (presenter or receiver)? | |
| Was the receiver on "Looking for Kipat" at the moment of touch? | |
| Receiver log has `reader_mode_started`? | |
| Presenter log has `hce_aid_selected`? | |
| `adb shell dumpsys nfc` excerpt (reader mode / HCE routing) saved to raw/? | |
