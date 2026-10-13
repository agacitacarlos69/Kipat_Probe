#!/usr/bin/env bash
# Generates the Kipat Signature Reveal design references with the official Higgsfield CLI.
#
# Prerequisites (run once, interactively, on a machine with a browser):
#   npm i -g @higgsfield/cli
#   higgsfield auth login
#   npx skills add higgsfield-ai/skills        # optional, for Claude Code
#   higgsfield workspace list && higgsfield workspace set <workspace_id>
#
# Credit discipline: each step prints a cost estimate and asks before spending.
# Outputs go to design/higgsfield/out/ (git-ignored). Only small stills are copied to
# references/. Never commit credentials or generated MP4s.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/out"
REFS="$HERE/references"
PROMPTS="$HERE/signature-reveal-prompt.md"
mkdir -p "$OUT" "$REFS"

command -v higgsfield >/dev/null || { echo "higgsfield CLI not found (npm i -g @higgsfield/cli)"; exit 1; }
higgsfield account status >/dev/null 2>&1 || { echo "Not signed in or no workspace selected: run 'higgsfield auth login' and 'higgsfield workspace set <id>'"; exit 1; }

# Extract the fenced block that follows a heading starting with the given prefix.
prompt_after() {
  awk -v h="$1" '
    index($0, h) == 1 { found = 1; next }
    found && /^```/ { if (inblock) exit; inblock = 1; next }
    found && inblock { print }
  ' "$PROMPTS"
}

KEYFRAME_PROMPT="$(prompt_after '## 1. Keyframe')"
MOTION_PROMPT="$(prompt_after '## 2. Motion concept')"
[ -n "$KEYFRAME_PROMPT" ] && [ -n "$MOTION_PROMPT" ] || { echo "Could not read prompts from $PROMPTS"; exit 1; }

confirm() { read -r -p "$1 [y/N] " a; [[ "$a" =~ ^[Yy]$ ]]; }

echo "== Step 1/2: keyframe (nano_banana_2_lite, 9:16, 1k)"
higgsfield generate cost nano_banana_2_lite --prompt "$KEYFRAME_PROMPT" --aspect_ratio 9:16 --resolution 1k
if confirm "Generate keyframe?"; then
  higgsfield generate create nano_banana_2_lite --prompt "$KEYFRAME_PROMPT" --aspect_ratio 9:16 --resolution 1k --wait --json > "$OUT/keyframe.json"
  KEY_URL="$(grep -oE 'https://[^"]+\.(png|jpg|jpeg|webp)' "$OUT/keyframe.json" | head -1)"
  [ -n "$KEY_URL" ] || { echo "No image URL in result; see $OUT/keyframe.json"; exit 1; }
  ext="${KEY_URL##*.}"
  curl -fsSL "$KEY_URL" -o "$OUT/keyframe.$ext"
  cp "$OUT/keyframe.$ext" "$REFS/signature-keyframe.$ext"
  echo "Keyframe: $REFS/signature-keyframe.$ext"
else
  echo "Skipped."; exit 0
fi

KEYFRAME_FILE="$(ls "$OUT"/keyframe.* | grep -v json | head -1)"

echo "== Step 2/2: motion concept (seedance_2_0_mini, 9:16, 480p, 4s, silent, ends on keyframe)"
higgsfield generate cost seedance_2_0_mini --prompt "$MOTION_PROMPT" --end-image "$KEYFRAME_FILE" \
  --aspect_ratio 9:16 --resolution 480p --duration 4 --generate_audio false
if confirm "Generate motion concept?"; then
  higgsfield generate create seedance_2_0_mini --prompt "$MOTION_PROMPT" --end-image "$KEYFRAME_FILE" \
    --aspect_ratio 9:16 --resolution 480p --duration 4 --generate_audio false \
    --wait --wait-timeout 20m --json > "$OUT/motion.json"
  VID_URL="$(grep -oE 'https://[^"]+\.mp4' "$OUT/motion.json" | head -1)"
  [ -n "$VID_URL" ] || { echo "No video URL in result; see $OUT/motion.json"; exit 1; }
  curl -fsSL "$VID_URL" -o "$OUT/signature-motion.mp4"
  echo "Video (not committed): $OUT/signature-motion.mp4"
  if command -v ffmpeg >/dev/null; then
    # Stills at the brief's beats; these small PNGs are what gets committed.
    for t in 0.15 0.45 0.80 1.10 1.40 1.80 3.50; do
      ffmpeg -loglevel error -y -ss "$t" -i "$OUT/signature-motion.mp4" -frames:v 1 -vf "scale=360:-2" "$REFS/signature-motion-${t}s.png"
    done
    echo "Stills written to $REFS"
  else
    echo "Install ffmpeg to extract reference stills."
  fi
fi

echo "Done. Record model, date, credits and the chosen direction in design/higgsfield/README.md."
