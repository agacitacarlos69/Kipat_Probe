# Device checklist — <manufacturer model>

Fill on the phone. Every row is PASS / FAIL / NOT TESTED plus a note. Never mark PASS from a
render, an emulator, or another device. Evidence lives in `screenshots/`, `raw/` (videos, logs; not
committed) and `performance.md`.

## Reveal over real apps
| Background | Previous app perceptible | Surface separates | Flash (white/black/bars) | Back returns | Result |
|---|---|---|---|---|---|
| Settings (light) | | | | | |
| Chrome (light page) | | | | | |
| Video app | | | | | |
| Launcher | | | | | |
| Settings (dark) | | | | | |

Backdrop classification for this device: LIVE / FROZEN / DIMMED / BLACK / HOME / OTHER: ____

## Templates (watch each video in raw/ at full speed and on the phone)
| Template | First response immediate? | Identity readable soon? | Stops completely? | Smooth (no dropped frames)? | Distracts from name? | Issue | Action |
|---|---|---|---|---|---|---|---|
| Contact Ripple | | | | | | | |
| Precision Orbit | | | | | | | |
| Line Assembly | | | | | | | |
| Aperture | | | | | | | |
| Spatial Card | | | | | | | |
| Minimal Fade | | | | | | | |

Device score (1–5) — Professionalism · Clarity · Smoothness · Speed · Distinctiveness · NFC relevance ·
Distraction (5 = none) · Readability · Trust · Overall preference — copy into
`design/reveal-templates/final-scorecard.md` under "Device score".

## Display reality (OLED/LCD, light + dark)
| Check | Result |
|---|---|
| Cobalt / Teal / Amber don't overpower identity | |
| Dark surfaces don't crush into the backdrop | |
| Grey (secondary/tertiary) text legible | |
| Verified green, revoked red, amber "Couldn't verify" readable and calm | |

## Buttons (verify behaviour, not just appearance)
| Action | Expected | Result |
|---|---|---|
| Connect | Becomes "Connected"; appears in Connections (in-memory demo store, resets with the process) | |
| Save contact | Opens the Android contact editor pre-filled; nothing saved until you confirm there | |
| Open profile | Opens Kipat at that person (or Connections), Reveal closes | |
| Close ✕ / Back / tap outside | Reveal closes, previous app shown, no dim left behind | |

## Haptics
| Moment | Feels right? (too strong / redundant / fine) |
|---|---|
| First frame tick | |
| Verified confirm (authentic cards only) | |
| Connect | |

## Style picker
| Check | Result |
|---|---|
| Live preview plays once on selection, then still | |
| Replay works | |
| Selected row and accent obvious | |
| Back without Apply keeps the old style | |
| Apply persists (re-open screen) | |
| Rotation keeps the draft | |
| Preview/Apply reachable, nothing clipped | |

## Accessibility
| Check | Result |
|---|---|
| Font 1.15 / 1.3 / 1.5 / 2.0 screenshots: nothing clipped, close doesn't overlap, actions reachable | |
| TalkBack order: Close → name → role → organisation → authenticity → Connect → Save contact | |
| TalkBack doesn't read initials, rings, lines or blades | |
| Animations off: every template readable at 350 ms, nothing blank, actions work | |

## Lifecycle
| Check | Result (see raw/*.txt) |
|---|---|
| Cold start: no crash, no flash | |
| Warm start | |
| Same card twice quickly → one Reveal, no restart | |
| Back / Home / shade mid-Reveal → no stuck dim, no zombie activity | |
| Rotation mid-Reveal → resumes, doesn't replay or duplicate | |

## Physical NFC (needs a writable NTAG21x tag)
| Step | Result |
|---|---|
| Tag type | |
| Write from Profile → Developer tools (payload `https://kipat.app/c/<id>…` + AAR) | |
| Tap with another app open → Android resolves Kipat | |
| Reveal over that app, translucent | |
| Cold (force-stopped) vs warm | |
| Same tag twice quickly | |
