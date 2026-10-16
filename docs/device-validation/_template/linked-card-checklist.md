# Linked Kipat card checklist

One phone with this build, NFC on, blank NTAG213/215/216. Record PASS / FAIL / NOT TESTED plus a
note for each row. Simulations and Robolectric are not evidence. Steps:
[`docs/nfc-card-identity-sync.md`](../../nfc-card-identity-sync.md#physical-test-instructions-not-yet-performed).

Build: `____`. Date: `____`. Phone and Android version: `____`. Card type: `____`.

## Linking
| Check | Result | Notes |
|---|---|---|
| Add Kipat card → "Card linked", "Uses your current Kipat profile" | | |
| NFC Tools shows exactly one URI `https://kipat.app/c/kc_…` and one AAR, with no name or `t=` | | |
| NDEF message size reported by NFC Tools (expected 69 bytes) | | |
| Same card again → "This card is already linked", nothing written | | |
| Card with other data → asks before replacing | | |
| Locked or read-only card → "This card is locked", nothing changed | | |

## Same card, no rewrite
| Change | Tap shows the change | Kipat open / another app / killed | Notes |
|---|---|---|---|
| Reveal style | | | |
| Accent | | | |
| Name or program | | | |
| Unpublish a field (clear it) | | | |

## Status and other phones
| Check | Result | Notes |
|---|---|---|
| Disabled card → "Kipat card unavailable", no profile | | |
| Enabled again → profile back | | |
| Removed card → "Kipat card removed" | | |
| Second Kipat phone → "This card can't be opened on this phone yet" (expected prototype limit) | | |
| Phone without Kipat → browser or Play Store (no web profile yet) | | |
| Self-contained card shows Out of date after an edit; Convert → follows edits | | |
| Kipat Tap between two phones still shows the current Reveal (regression) | | |

Logs: `adb logcat -s KipatCards KipatIdentity KipatReveal` saved to `raw/` (do not commit).
