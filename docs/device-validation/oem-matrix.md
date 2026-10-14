# OEM matrix — translucent Reveal over other apps

Backdrop: LIVE (previous app keeps drawing) · FROZEN (last frame) · DIMMED · BLACK · HOME (launcher
shown instead) · OTHER. A row is only filled from a physical device running the build named in it.

| OEM | Model | OS / skin | Android | Build tested | Backdrop | Cold start | Warm start | Result | Notes |
|---|---|---|---|---|---|---|---|---|---|
| OPPO | (diagnostic probe era) | ColorOS | — | original Java probe, not this app | NOT TESTED for this build | NOT TESTED | ~190 ms warm launch measured for the old probe only | Historical | The only on-device number in the project's history; does not validate the Compose Reveal |
| Samsung | — | One UI | — | — | NOT TESTED | NOT TESTED | NOT TESTED | Unverified | |
| Google | Pixel | Stock | — | — | NOT TESTED | NOT TESTED | NOT TESTED | Unverified | |
| Xiaomi | — | HyperOS / MIUI | — | — | NOT TESTED | NOT TESTED | NOT TESTED | Unverified | |
| OPPO | — | ColorOS | — | — | NOT TESTED | NOT TESTED | NOT TESTED | Unverified | |

## Phone-to-phone NFC

Filled only from two physical phones running the build named in the row
([two-phone checklist](_template/two-phone-checklist.md)). Reader mode suppresses the system tag
viewer? AID reaches Kipat? Record each direction separately.

| Presenter (OEM / model / Android / skin) | Receiver (OEM / model / Android / skin) | Build | System "Empty tag" suppressed | AID reached presenter | B receives A | A receives B | Result | Notes |
|---|---|---|---|---|---|---|---|---|
| — | — | — | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | Unverified | No two-phone test has been run |
