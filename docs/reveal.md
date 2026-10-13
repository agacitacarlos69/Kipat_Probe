# Reveal

One renderer (`RevealScreen`) serves every entry point: NFC tag, debug deep link, Preview
(Home/Profile) and Replay (Connections).

![The six Reveal templates at 100, 250, 450, 700 ms and settled, light theme (Robolectric renders of the Compose runtime)](images/reveal-templates-light.png)

*Rows top to bottom: Contact Ripple, Precision Orbit, Line Assembly, Aperture, Spatial Card,
Minimal Fade. JVM (Robolectric) renders of the real Compose code, not Higgsfield output and not
device captures; the scrim sits over a blank window here, not a real app. Dark theme:
[images/reveal-templates-dark.png](images/reveal-templates-dark.png).*

## Payload

`RevealPayload`: identityId, cardId, displayName, subtitle, organization, initials, avatarUri
(reserved; initials are the fallback), `AuthenticityState`, template, theme, source, isDemo,
primary/secondary CTA labels, connectable.

- In-app launches use `RevealLauncher`, which encodes the payload as string extras **plus a
  per-process nonce**. Extras without the nonce are ignored, so another app cannot launch a
  spoofed "Verified" Reveal through the exported activity.
- `RevealPayloadCodec.decode` never throws: blank name → "Kipat card", unknown template →
  the default template (legacy v1/v2 IDs map to the closest current template), unknown authenticity → UNKNOWN, invalid card id / non-https/content avatar → dropped,
  text sanitized and clamped.
- A tag/link that can't be resolved shows a neutral "Kipat card · Card ID" shell, "Not verified",
  and a *Done* button (no Connect).

## Templates

Six interpretations of one card (design: `design/reveal-templates/`). Each has exactly **one primary
motion**, plus the surface resolving and text staging. Shared: `RevealStage` (one renderer),
`effects/RevealEffects.kt` (one file of primitives), `IdentityBlock` and `RevealActions`.

| Template (wire ID) | Primary motion | Surface | Name | Settled |
|---|---|---|---|---|
| **Contact Ripple** (`ripple`, default) | Contact dot; one ring from the card's top centre is the edge of a circular reveal; a fainter second ring | circular reveal | fade | 1220 ms |
| Precision Orbit (`orbit`) | Two symmetric arcs turn 90° once, close into a ring around the avatar, turn accent, settle as a 1 dp frame | fade + 0.99→1 | wipe | 1400 ms |
| Line Assembly (`lines`) | Accent corner mark; top and left drafting lines drawn with 12 dp overshoot; right and bottom fade in; lines leave | fade | wipe | 1400 ms |
| Aperture (`aperture`) | Six flat blades over the avatar turn −5°, open to the rim (+12°), fade as slivers; ≤ 600 ms on screen | fade | fade | 1240 ms |
| Spatial Card (`spatial`) | Card arrives tilted 6° (pivot: bottom edge) showing a 3 dp edge; settles flat on a ζ 0.9 spring; shadow forms (light) | tilt | fade | 1200 ms |
| Minimal Fade (`minimal-fade`, low-power) | None: fade + 10 dp rise | fade-rise | fade | 620 ms |

Legacy IDs on already-written cards: `drip`, `particle-bloom`, `soft-glow`, `liquid-logo`,
`pixel-build`, `constellation` → default; `line-trace` → Line Assembly.

Colour: one ink per theme for every primitive (graphite on a paper scrim in light, mist on a
graphite scrim in dark); the person's accent only at completion (orbit ring, corner mark) and on
Connect. No particles, glow, gradients, blur or sound in any template.

**Picker** (Profile → Reveal style): each row shows a static thumbnail drawn with the template's
own primitives at its key frame, the name and one line of character. Tapping a row only selects;
**Preview** plays the selection on your own card; **Apply** makes it what others see. Accent works
the same way.

<img src="images/reveal-style-picker.png" width="300" alt="Reveal style picker: six templates with thumbnails, Preview and Apply">

## Timeline (product targets, not measurements)

Per-template beats are in each `design/reveal-templates/<template>.md`; card/text timing is
`RevealTimeline`. Motion curves (`KipatEasing`): M3 standard, M3 emphasized decelerate, M3
emphasized, M3 emphasized accelerate, and a 0.9-damped spring (stiffness 300) for Spatial Card.
Nothing overshoots visibly.

Actions: **Connect** (primary; "Done" for an unresolved card), **Save contact** (Android contact
editor via ACTION_INSERT), **Open profile** (only when there is a Kipat profile to open: an
existing connection or your own preview). Card, scrim and ink follow the system light/dark theme.

The spec's "0–100 ms activity launch" target is **not** claimed. The earlier OPPO probe measured
~190 ms warm launch; nothing has been measured on a device for this build.

## Fallbacks

- **Reduced motion** (`ValueAnimator.areAnimatorsEnabled() == false`): each template's own
  equivalent, all 200 ms and readable from the first frame: Contact Ripple → the contact dot
  fades in and out once; Precision Orbit → the settled ring fades in; Line Assembly → the four
  lines fade out; Aperture and Minimal Fade → crossfade; Spatial Card → 0.98→1 scale + opacity.
  Nothing moves, rotates, masks or tilts.
- **Low-end** (low-RAM flag or ≤ 4 cores): same choreography (every primitive is a handful of
  draw calls, no offscreen buffers, no per-frame allocation); the animated shadow is skipped.
  Minimal Fade is the recommended low-power template.
- Rotation: elapsed time is saved, the Reveal resumes rather than replays.
- Home/app switch: activity finishes in `onStop`.
- Duplicate NFC scan of the same card within 1.5 s: ignored.
- Identity text is always in the layout (alpha-animated only) and exposed to TalkBack; the close
  button is always available.

## Instrumentation

`RevealTrace` logs to `KipatReveal` and emits `android.os.Trace` sections:
`activity_create` (with `launch→create` for in-app launches), `content_set`, `first_frame`,
`first_reveal_visual`, `identity_readable` (name and role fully shown), `reveal_complete`. Profile → Developer tools lists this session's
measurements. Use Perfetto on real devices for authoritative numbers.

## Smoke tests

- JVM: `AppSmokeTest` (Robolectric) — manifest routing, cold deep-link launch, dynamic content,
  dismiss, spoofed-extra rejection.
- Device (not yet run): `adb shell am start -W -a android.intent.action.VIEW -d "kipatprobe://tap/TEST123"`,
  then check it appears over the previous app and Back/✕ returns to it.
