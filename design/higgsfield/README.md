# Higgsfield art direction — Kipat Signature Reveal

Higgsfield is used for **visual/motion reference only**. The shipped Reveal is native Compose
(`app/src/main/java/com/kipat/probe/reveal/`) driven by a dynamic `RevealPayload`. No
Higgsfield output is bundled in the APK, and generated video is never committed.

```
Higgsfield keyframe + clip  →  extract motion language  →  Compose primitives  →  dynamic payload
```

## Status (2026-10-13)

| Item | State |
|---|---|
| Higgsfield CLI | Installed in the build container: `@higgsfield/cli` 1.1.26 |
| Companion skills | Installed globally (`npx skills add higgsfield-ai/skills -g --agent claude-code`), not in the repo |
| Account | Reachable read-only through the Higgsfield connector: private workspace, 130 credits, *basic* plan, **0 prior generations** |
| CLI auth | **Blocked.** `higgsfield auth login` is browser OAuth with a loopback callback to the machine running the CLI; the cloud container's loopback is unreachable from a user's browser. `generate cost` returns `Not authenticated`. |
| Generation performed | **No.** Nothing was generated. No image or video in this folder came from Higgsfield. |
| Prompts | Final, in `signature-reveal-prompt.md` |
| Script | `generate.sh` — cost check + confirmation before every paid step |

The native implementation was built from the written brief so engineering wasn't blocked. When
the reference exists, compare it against the native frames in `docs/images/reveal-strip.png`
and update `implementation-notes.md`.

## To generate (≈ one keyframe + one short clip)

On a machine with a browser:

```bash
npm i -g @higgsfield/cli
higgsfield auth login
higgsfield workspace list            # pick the workspace that holds the credits
higgsfield workspace set <workspace_id>
./design/higgsfield/generate.sh      # prints cost, asks before each step
```

Chosen models (lowest cost that is adequate for concept work, verified against the live model
catalog on 2026-10-13):

| Step | Model | Params | Why |
|---|---|---|---|
| Keyframe | `nano_banana_2_lite` (Nano Banana 2 Lite) | 9:16, 1k | Budget image model; clean flat UI frame; becomes the clip's end frame |
| Motion | `seedance_2_0_mini` (Seedance 2.0 Mini) | 9:16, 480p, 4 s, `generate_audio false`, `--end-image keyframe` | Budget video tier that accepts an **end image**, so the clip lands on our card layout instead of inventing one |

Budget: 1 primary concept. Generate at most 2 alternates, and only if the first misses the brief
(e.g. explodes outward, adds text, or adds a device mockup).

## After generating — record here

- Generation date: _(fill in)_
- Credits spent: _(from `higgsfield account transactions`)_
- Files: `references/signature-keyframe.*`, `references/signature-motion-*.png` (stills ≤ 360 px wide)
- MP4: kept outside git (`design/higgsfield/out/`, ignored)
- Matches brief? _(yes/no + notes)_
- Licensing: design-only reference; not shippable as product art without review

## Files

- `signature-reveal-prompt.md` — the brief, the exact keyframe and motion prompts, rejected directions
- `implementation-notes.md` — how each beat maps to Compose primitives, and what to compare once a reference exists
- `generate.sh` — reproducible, credit-guarded generation
- `references/` — small committed stills (empty until a real generation happens)
