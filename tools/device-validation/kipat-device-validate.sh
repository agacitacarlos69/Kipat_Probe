#!/usr/bin/env bash
# Kipat real-device validation kit.
#
# Runs every automatable step of docs/device-validation/README.md against ONE physical Android
# phone over adb, and writes evidence to docs/device-validation/<device>/:
#   device-info.md, performance.md (measured numbers only), screenshots/, raw/ (videos, logs;
#   git-ignored), checklist.md (what a person must still judge on the phone).
#
# It never claims a visual or interaction result: those need human eyes and fingers.
#
# Usage:
#   tools/device-validation/kipat-device-validate.sh [phase ...]
#   phases: info install shell apps templates timing repeat interrupt rotation font motion all
#           nfcprofile (opt-in, not in 'all': needs a person and an NTAG215/216; NFC_WAIT_S=120)
#   DRY_RUN=1  print the adb commands instead of running them (no phone needed)
#   APK=path   APK to install (default: app/build/outputs/apk/debug/app-debug.apk)
#
# Device settings it changes (dark mode, font scale, animator scale, rotation) are restored on
# exit, including on Ctrl-C.
set -euo pipefail

PKG=com.kipat.probe
REVEAL=$PKG/.TapExperienceActivity
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
APK=${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}
ADB=${ADB:-adb}
DRY=${DRY_RUN:-0}
TEMPLATES=(ripple orbit lines aperture spatial minimal-fade)
# Representative accent per template so every accent appears in both modes without a full matrix.
declare -A ACCENT=([ripple]=night [orbit]=aurora [lines]=ember [aperture]=night [spatial]=aurora [minimal-fade]=ember)
SETTLE_S=2.5 # longest template settles at 1.2 s; leave margin for launch

log() { printf '\033[1m[kipat]\033[0m %s\n' "$*" >&2; }

a() { # adb wrapper honouring DRY_RUN
  if [[ $DRY == 1 ]]; then echo "adb $*" >&2; [[ "$*" == *"getprop"* ]] && echo "dry"; return 0; fi
  "$ADB" "$@"
}
sh_() { a shell "$@" | tr -d '\r'; }

require_one_device() {
  if [[ $DRY == 1 ]]; then DEVICE_DIR=${DRY_OUT:-/tmp/kipat-device-dry-run}; return; fi
  command -v "$ADB" >/dev/null || { log "adb not found (set ADB=/path/to/adb)"; exit 2; }
  local n
  n=$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l)
  if [[ $n -ne 1 ]]; then
    log "need exactly one physical device in 'adb devices' (found $n). Emulators do not count."
    exit 2
  fi
  if [[ $(sh_ getprop ro.kernel.qemu) == 1 || $(sh_ getprop ro.boot.qemu) == 1 ]]; then
    log "this is an emulator; device validation needs a physical phone"; exit 2
  fi
  local slug
  slug=$(printf '%s-%s' "$(sh_ getprop ro.product.manufacturer)" "$(sh_ getprop ro.product.model)" | tr 'A-Z ' 'a-z-' | tr -cd 'a-z0-9-')
  DEVICE_DIR=$ROOT/docs/device-validation/$slug
}

prepare_dirs() { mkdir -p "$DEVICE_DIR/screenshots" "$DEVICE_DIR/raw"; PERF_CSV=$DEVICE_DIR/raw/timings.csv; }

# ---------------------------------------------------------------------------- settings + restore
ORIG_NIGHT="" ORIG_FONT="" ORIG_ANIM="" ORIG_TRANS="" ORIG_WIN="" ORIG_ROT="" ORIG_USER_ROT=""
save_settings() {
  ORIG_NIGHT=$(sh_ cmd uimode night | awk '{print $NF}')
  ORIG_FONT=$(sh_ settings get system font_scale)
  ORIG_ANIM=$(sh_ settings get global animator_duration_scale)
  ORIG_TRANS=$(sh_ settings get global transition_animation_scale)
  ORIG_WIN=$(sh_ settings get global window_animation_scale)
  ORIG_ROT=$(sh_ settings get system accelerometer_rotation)
  ORIG_USER_ROT=$(sh_ settings get system user_rotation)
}
restore_settings() {
  [[ -z "${ORIG_FONT}" ]] && return 0
  log "restoring device settings"
  a shell cmd uimode night "${ORIG_NIGHT:-auto}" >/dev/null || true
  for kv in "system font_scale ${ORIG_FONT}" "global animator_duration_scale ${ORIG_ANIM}" \
            "global transition_animation_scale ${ORIG_TRANS}" "global window_animation_scale ${ORIG_WIN}" \
            "system accelerometer_rotation ${ORIG_ROT}" "system user_rotation ${ORIG_USER_ROT}"; do
    set -- $kv
    if [[ "$3" == null || -z "$3" ]]; then a shell settings delete "$1" "$2" >/dev/null || true
    else a shell settings put "$1" "$2" "$3" >/dev/null || true; fi
  done
}
trap restore_settings EXIT

night() { a shell cmd uimode night "$1" >/dev/null; sleep 1; }

# ---------------------------------------------------------------------------- helpers
link() { # link <card> [query]
  local q=${2:-}; printf 'kipatprobe://tap/%s%s' "$1" "${q:+?$q}"
}
reveal() { # reveal <name> <uri> → am start -W timing row + logcat markers
  local name=$1 uri=$2 out
  a logcat -c || true
  out=$(sh_ am start -W -a android.intent.action.VIEW -d "'$uri'" "$PKG" 2>&1 || true)
  local total wait this
  total=$(awk -F': ' '/TotalTime/{print $2}' <<<"$out"); wait=$(awk -F': ' '/WaitTime/{print $2}' <<<"$out")
  this=$(awk -F': ' '/ThisTime/{print $2}' <<<"$out")
  sleep "$SETTLE_S"
  local markers
  markers=$(a logcat -d -s KipatReveal:I 2>/dev/null | tr -d '\r' | sed -n 's/.*KipatReveal: //p' | tr '\n' ';' || true)
  echo "$name,${this:-},${total:-},${wait:-},\"$markers\"" >>"$PERF_CSV"
}
shot() { # shot <file-stem>
  if [[ $DRY == 1 ]]; then echo "adb exec-out screencap -p > screenshots/$1.png" >&2; return; fi
  "$ADB" exec-out screencap -p >"$DEVICE_DIR/screenshots/$1.png"
}
record() { # record <stem> <seconds> <command...> — screenrecord around a trigger, pulled to raw/
  local stem=$1 secs=$2; shift 2
  a shell "screenrecord --time-limit $secs /sdcard/kipat-$stem.mp4" &
  local rec=$!
  sleep 0.8
  "$@"
  wait "$rec" || true
  a pull "/sdcard/kipat-$stem.mp4" "$DEVICE_DIR/raw/$stem.mp4" >/dev/null 2>&1 || true
  a shell rm -f "/sdcard/kipat-$stem.mp4" || true
}
back() { a shell input keyevent KEYCODE_BACK; sleep 0.6; }
home() { a shell input keyevent KEYCODE_HOME; sleep 0.8; }
reveal_count() { sh_ dumpsys activity activities | grep -c "TapExperienceActivity" || true; }

# ---------------------------------------------------------------------------- phases
phase_info() {
  log "device info"
  local f=$DEVICE_DIR/device-info.md
  {
    echo "# Device info"
    echo
    echo "Collected by \`tools/device-validation/kipat-device-validate.sh\` on $(date -u +%F)."
    echo
    echo "| Property | Value |"
    echo "|---|---|"
    echo "| Manufacturer | $(sh_ getprop ro.product.manufacturer) |"
    echo "| Model | $(sh_ getprop ro.product.model) ($(sh_ getprop ro.product.device)) |"
    echo "| Android | $(sh_ getprop ro.build.version.release) (SDK $(sh_ getprop ro.build.version.sdk)) |"
    echo "| Build | $(sh_ getprop ro.build.display.id) |"
    echo "| Skin | ColorOS: $(sh_ getprop ro.build.version.opporom) · One UI: $(sh_ getprop ro.build.version.oneui) · MIUI/HyperOS: $(sh_ getprop ro.miui.ui.version.name) |"
    echo "| Resolution | $(sh_ wm size | sed 's/.*: //' | tr '\n' ' ') |"
    echo "| Density | $(sh_ wm density | sed 's/.*: //' | tr '\n' ' ') |"
    echo "| Font scale | $(sh_ settings get system font_scale) |"
    echo "| Animator scale | $(sh_ settings get global animator_duration_scale) |"
    echo "| Navigation mode | $(sh_ settings get secure navigation_mode) (0 = 3-button, 1 = 2-button, 2 = gestures) |"
    echo "| NFC hardware | $(sh_ pm list features | grep -q 'android.hardware.nfc$' && echo yes || echo no) |"
    echo "| NFC state | $(sh_ dumpsys nfc 2>/dev/null | grep -m1 -iE 'mState=|^ *state' | sed 's/^ *//') |"
    echo "| Refresh rate | $(sh_ dumpsys display | grep -m1 -oE 'refreshRate=[0-9.]+' ) |"
  } >"$f"
  log "wrote $f"
}

phase_install() {
  log "install $APK"
  [[ $DRY == 1 || -f "$APK" ]] || { log "APK missing: run ./gradlew :app:assembleDebug"; exit 2; }
  a install -r "$APK"
  a shell am start -W -n "$PKG/.MainActivity" | tr -d '\r' >"$DEVICE_DIR/raw/main-launch.txt" || true
  sleep 1.5; shot app-home-launch
}

phase_shell() {
  log "shell screens (light + dark)"
  for mode in no yes; do
    night "$mode"; local m; m=$([[ $mode == yes ]] && echo dark || echo light)
    a shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 1.2; shot "shell-$m-home"
  done
  log "  navigate tabs, Profile → Reveal style, Edit profile by hand; see checklist.md"
}

phase_apps() {
  log "Reveal over real apps (light app, dark app, Chrome, video, Settings, launcher)"
  local uri; uri=$(link demo "t=ripple&th=night")
  open_app() { a shell "$@" >/dev/null 2>&1 || true; sleep 2; }
  night no
  open_app am start -a android.settings.SETTINGS;                         reveal over-settings-light "$uri"; shot over-settings-light; back
  open_app am start -a android.intent.action.VIEW -d https://www.wikipedia.org; reveal over-chrome-light "$uri"; shot over-chrome-light; back
  open_app monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1; reveal over-video "$uri"; shot over-video; back
  home; reveal over-launcher "$uri"; shot over-launcher; back
  night yes
  open_app am start -a android.settings.SETTINGS;                         reveal over-settings-dark "$uri"; shot over-settings-dark; back
  night no
}

phase_templates() {
  log "all six templates, light + dark, with representative accents (videos to raw/)"
  for mode in no yes; do
    night "$mode"; local m; m=$([[ $mode == yes ]] && echo dark || echo light)
    a shell am start -a android.settings.SETTINGS >/dev/null; sleep 1.5
    for t in "${TEMPLATES[@]}"; do
      local uri; uri=$(link demo "t=$t&th=${ACCENT[$t]}")
      record "$t-$m" 5 reveal "$t-$m" "$uri"
      shot "$t-$m-settled"; back
    done
  done
  night no
  log "hard cases (light): long name, company, revoked, checking, unverified, no context"
  local cases=(
    "case-long-name|KPT-LONG|n=Alexandra%20Marie%20Villanueva-Santiago&s=Senior%20Product%20Designer%2C%20Platform&o=Polytechnic%20University%20of%20the%20Philippines&a=authentic"
    "case-company|KPT-ORG|n=Globe%20Labs&s=Product%20and%20engineering%20studio&o=Taguig&k=org&t=lines&a=authentic"
    "case-revoked|KPT-REV|n=Rafael%20Lim&s=Creator&o=Studio%20Lim&a=revoked"
    "case-checking|KPT-CHK|n=Sam%20Reyes&s=Designer&o=Acme&a=checking"
    "case-unverified|KPT-UNV|n=Jonas%20Reyes&s=BS%20Architecture&o=UST"
    "case-no-context|KPT-NOC|n=Kim%20Aquino"
  )
  for c in "${cases[@]}"; do
    IFS='|' read -r stem card q <<<"$c"
    reveal "$stem" "$(link "$card" "$q")"; shot "$stem"; back
  done
}

phase_nfcprofile() {
  log "NFC full profile test (needs a person and an NTAG215/216; see README 'Full profile NFC test')"
  a shell am start -W -n "$PKG/.MainActivity" --es kipat.nav.destination profile/developer/nfc-profile >/dev/null
  sleep 1.5; shot nfc-profile-screen
  local wait_s=${NFC_WAIT_S:-120}
  log "  on the phone: Load sample profile → Write profile card → hold the card until PROFILE WRITE VERIFIED;"
  log "  Read profile card → Preview Reveal; then Home, tap the card (warm); am force-stop $PKG, tap again (cold)."
  log "  capturing logcat for ${wait_s}s → raw/nfc-profile-log.txt (shapes and sizes only, no profile fields)"
  a logcat -c
  if [[ $DRY == 1 ]]; then echo "adb logcat -s KipatNfcProfile:I KipatReveal:I  (for ${wait_s}s)" >&2; return; fi
  timeout "$wait_s" "$ADB" logcat -s KipatNfcProfile:I KipatReveal:I >"$DEVICE_DIR/raw/nfc-profile-log.txt" || true
  shot nfc-profile-after
}

phase_timing() {
  log "cold vs warm start (5 each)"
  local uri; uri=$(link demo "t=ripple")
  for i in 1 2 3 4 5; do
    a shell am force-stop "$PKG"; sleep 1
    reveal "cold-$i" "$uri"; back
    reveal "warm-$i" "$uri"; back
  done
}

phase_repeat() {
  log "rapid repeated trigger (same card twice, then a different card)"
  local uri; uri=$(link KPT-REPEAT "n=Sam%20Reyes")
  a shell "am start -a android.intent.action.VIEW -d '$uri' $PKG; am start -a android.intent.action.VIEW -d '$uri' $PKG" >/dev/null
  sleep "$SETTLE_S"; shot repeat-same-card
  echo "repeat-same-card instances=$(reveal_count)" >>"$DEVICE_DIR/raw/repeat.txt"
  reveal repeat-other-card "$(link KPT-REPEAT2 "n=Ana%20Cruz")"; shot repeat-other-card
  echo "after-other-card instances=$(reveal_count)" >>"$DEVICE_DIR/raw/repeat.txt"
  back; echo "after-back instances=$(reveal_count)" >>"$DEVICE_DIR/raw/repeat.txt"
}

phase_interrupt() {
  log "Back / Home / notification shade mid-Reveal"
  local uri; uri=$(link demo "t=orbit")
  a shell am start -a android.intent.action.VIEW -d "'$uri'" "$PKG" >/dev/null; sleep 0.4; back
  echo "back-mid-reveal instances=$(reveal_count)" >>"$DEVICE_DIR/raw/interrupt.txt"
  a shell am start -a android.intent.action.VIEW -d "'$uri'" "$PKG" >/dev/null; sleep 0.4; home
  echo "home-mid-reveal instances=$(reveal_count)" >>"$DEVICE_DIR/raw/interrupt.txt"; shot after-home-mid-reveal
  a shell am start -a android.intent.action.VIEW -d "'$uri'" "$PKG" >/dev/null; sleep 0.4
  a shell cmd statusbar expand-notifications; sleep 1; a shell cmd statusbar collapse; sleep 1.5
  shot after-shade-mid-reveal; echo "shade-mid-reveal instances=$(reveal_count)" >>"$DEVICE_DIR/raw/interrupt.txt"; back
}

phase_rotation() {
  log "rotation during Reveal (resumes from saved elapsed time; must not replay or duplicate)"
  a shell settings put system accelerometer_rotation 0
  local uri; uri=$(link demo "t=lines")
  a shell am start -a android.intent.action.VIEW -d "'$uri'" "$PKG" >/dev/null; sleep 0.5
  a shell settings put system user_rotation 1; sleep 1.5; shot rotation-landscape
  a shell settings put system user_rotation 0; sleep 1.5; shot rotation-portrait
  echo "rotation instances=$(reveal_count)" >>"$DEVICE_DIR/raw/rotation.txt"; back
}

phase_font() {
  log "font scale 1.0 / 1.15 / 1.3 / 1.5 / 2.0 (long name + revoked + sample chip)"
  local uri; uri=$(link KPT-FONT "n=Alexandra%20Marie%20Villanueva-Santiago&s=Senior%20Product%20Designer&o=Polytechnic%20University%20of%20the%20Philippines&t=aperture&a=revoked")
  for s in 1.0 1.15 1.3 1.5 2.0; do
    a shell settings put system font_scale "$s"; sleep 1.5
    reveal "font-$s" "$uri"; shot "font-$s"; back
  done
  a shell settings put system font_scale "${ORIG_FONT:-1.0}"
}

phase_motion() {
  log "animations off (Settings → Accessibility → Remove animations equivalent)"
  for k in animator_duration_scale transition_animation_scale window_animation_scale; do a shell settings put global "$k" 0; done
  for t in "${TEMPLATES[@]}"; do
    a shell am start -a android.intent.action.VIEW -d "'$(link demo "t=$t")'" "$PKG" >/dev/null
    sleep 0.35; shot "reduced-$t-350ms"; sleep 1; shot "reduced-$t-settled"; back
  done
  for k in animator_duration_scale transition_animation_scale window_animation_scale; do a shell settings put global "$k" "${ORIG_ANIM:-1}"; done
}

write_reports() {
  local perf=$DEVICE_DIR/performance.md
  {
    echo "# Performance (measured)"
    echo
    echo "Only numbers captured on this device. \`am start -W\` reports ThisTime/TotalTime/WaitTime"
    echo "(intent → first frame of the Reveal activity). The KipatReveal markers are logged by"
    echo "\`RevealTrace\` (ms since activity_create): content_set, first_frame, first_reveal_visual,"
    echo "identity_readable, reveal_complete. Targets are goals, not pass/fail."
    echo
    echo "| Run | ThisTime | TotalTime | WaitTime | KipatReveal markers |"
    echo "|---|---|---|---|---|"
    [[ -f $PERF_CSV ]] && while IFS=, read -r name this total wait markers; do
      echo "| $name | ${this:-n/a} | ${total:-n/a} | ${wait:-n/a} | ${markers//\"/} |"
    done <"$PERF_CSV"
    echo
    echo "Targets: first meaningful visual ≲ 200 ms · identity readable ≲ 900 ms · actions ≲ 1500 ms."
  } >"$perf"
  [[ -f $DEVICE_DIR/checklist.md ]] || cp "$ROOT/docs/device-validation/_template/checklist.md" "$DEVICE_DIR/checklist.md"
  log "wrote $perf; now judge $DEVICE_DIR/checklist.md on the phone"
}

main() {
  local phases=("$@"); [[ ${#phases[@]} -eq 0 ]] && phases=(all)
  [[ ${phases[0]} == all ]] && phases=(info install shell apps templates timing repeat interrupt rotation font motion)
  require_one_device; prepare_dirs
  [[ $DRY == 1 ]] || save_settings
  for p in "${phases[@]}"; do "phase_$p"; done
  write_reports
}
main "$@"
