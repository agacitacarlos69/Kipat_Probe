#!/usr/bin/env bash
# Kipat Tap — ambient two-phone validation (docs/nfc/ambient-peer-feasibility.md).
#
# The PRIMARY acceptance test: neither person opens Kipat, presses Present or Receive; they touch
# unlocked phones while using other apps, and BOTH phones must show the other person's Reveal.
# This script prepares two physical phones, puts unrelated apps in front, captures logs and
# evidence around each touch, and records what happened. It never performs or fakes the touch.
#
# Usage:
#   PHONE_A_SERIAL=<serial> PHONE_B_SERIAL=<serial> tools/device-validation/kipat-ambient-two-phone-validate.sh [scenario ...]
#   scenarios: info install setup apps home killed locked forcestop all   (all = info install setup apps home killed locked)
#   DRY_RUN=1   print every adb command instead of running it (no phones needed)
#   APK=path    APK to install (default app/build/outputs/apk/debug/app-debug.apk)
#   TOUCH_WAIT_S seconds of logs captured after you confirm the touch (default 15)
#
# Every adb call names a serial: a command can never run on the wrong phone.
set -euo pipefail

PKG=com.kipat.probe
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
APK=${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}
ADB=${ADB:-adb}
DRY=${DRY_RUN:-0}
WAIT_S=${TOUCH_WAIT_S:-15}
TAGS=(KipatAmbientNfc:I KipatType4:I KipatHce:I KipatPeerProtocol:I KipatPeerReader:I KipatPeerNfc:I KipatTapLaunch:I KipatReveal:I ActivityTaskManager:W)

log() { printf '\033[1m[kipat-tap]\033[0m %s\n' "$*" >&2; }
on() { # on <serial> <adb args...>
  local s=$1; shift
  if [[ $DRY == 1 ]]; then echo "adb -s $s $*" >&2; [[ "$*" == *getprop* ]] && echo "dry"; return 0; fi
  "$ADB" -s "$s" "$@"
}
prop() { on "$1" shell getprop "$2" | tr -d '\r'; }
pause() { # pause <message>
  log "$*"
  if [[ $DRY == 1 ]]; then echo "(waits for Enter)" >&2; return; fi
  read -r -p "    press Enter when done… " _ </dev/tty
}

check() {
  A=${PHONE_A_SERIAL:-}; B=${PHONE_B_SERIAL:-}
  [[ -n $A && -n $B ]] || { log "set PHONE_A_SERIAL and PHONE_B_SERIAL (adb devices -l)"; exit 2; }
  [[ $A != "$B" ]] || { log "the two serials must be different phones"; exit 2; }
  if [[ $DRY == 1 ]]; then OUT=${DRY_OUT:-/tmp/kipat-ambient-dry-run}; mkdir -p "$OUT/raw" "$OUT/screenshots"; return; fi
  command -v "$ADB" >/dev/null || { log "adb not found"; exit 2; }
  for s in "$A" "$B"; do
    "$ADB" devices | awk 'NR>1 && $2=="device"{print $1}' | grep -qx "$s" || { log "$s not connected/authorised"; exit 2; }
    [[ $(prop "$s" ro.kernel.qemu) != 1 && $(prop "$s" ro.boot.qemu) != 1 ]] || { log "$s is an emulator; physical phones only"; exit 2; }
  done
  slug() { printf '%s-%s' "$(prop "$1" ro.product.manufacturer)" "$(prop "$1" ro.product.model)" | tr 'A-Z ' 'a-z-' | tr -cd 'a-z0-9-'; }
  OUT=$ROOT/docs/device-validation/ambient/$(slug "$A")__$(slug "$B")
  mkdir -p "$OUT/raw" "$OUT/screenshots"
}

shot() { # shot <serial> <stem>
  if [[ $DRY == 1 ]]; then echo "adb -s $1 exec-out screencap -p > screenshots/$2.png" >&2; return; fi
  "$ADB" -s "$1" exec-out screencap -p >"$OUT/screenshots/$2.png"
}

scenario_info() {
  log "device info, NFC/HCE features, NFC service state"
  {
    echo "# Kipat Tap ambient — devices"; echo
    echo "| Phone | Serial | Manufacturer | Model | Android | SDK | Skin | NFC | HCE |"
    echo "|---|---|---|---|---|---|---|---|---|"
    for p in A B; do
      local s; s=$([[ $p == A ]] && echo "$A" || echo "$B")
      local f; f=$(on "$s" shell pm list features | tr -d '\r')
      local skin; skin=$(prop "$s" ro.miui.ui.version.name)$(prop "$s" ro.mi.os.version.name)$(prop "$s" ro.build.version.oneui)$(prop "$s" ro.build.version.opporom)
      echo "| $p | \`$s\` | $(prop "$s" ro.product.manufacturer) | $(prop "$s" ro.product.model) | $(prop "$s" ro.build.version.release) | $(prop "$s" ro.build.version.sdk) | ${skin:-—} | $(grep -q 'android.hardware.nfc$' <<<"$f" && echo yes || echo no) | $(grep -q 'android.hardware.nfc.hce' <<<"$f" && echo yes || echo no) |"
    done
  } >"$OUT/device-info.md"
  for p in A B; do
    local s; s=$([[ $p == A ]] && echo "$A" || echo "$B")
    on "$s" shell dumpsys nfc >"$OUT/raw/dumpsys-nfc-$p-before.txt" 2>/dev/null || true
  done
}

scenario_install() {
  [[ $DRY == 1 || -f "$APK" ]] || { log "APK missing: ./gradlew :app:assembleDebug"; exit 2; }
  for s in "$A" "$B"; do log "install on $s"; on "$s" install -r "$APK" >/dev/null; done
}

scenario_setup() {
  for s in "$A" "$B"; do on "$s" shell am start -W -n "$PKG/.MainActivity" >/dev/null; done
  pause "ON BOTH PHONES: give each a DIFFERENT profile (Profile → Edit profile), then Home → 'Turn on Kipat Tap' → Enable. NFC on. (Android 17 never dispatches to an app that was never opened.)"
}

touch_round() { # touch_round <label> <instructions>
  local label=$1 what=$2
  for s in "$A" "$B"; do on "$s" logcat -c || true; done
  pause "[$label] $what  Then TOUCH THE PHONES NOW (backs together, upper halves), hold until both react, and press Enter."
  if [[ $DRY == 1 ]]; then
    for s in "$A" "$B"; do echo "adb -s $s logcat -d -s ${TAGS[*]}" >&2; done
  else
    sleep "$WAIT_S"
    on "$A" logcat -d -s "${TAGS[@]}" >"$OUT/raw/$label-A.log" || true
    on "$B" logcat -d -s "${TAGS[@]}" >"$OUT/raw/$label-B.log" || true
  fi
  shot "$A" "$label-A-after-touch"; shot "$B" "$label-B-after-touch"
  for s in "$A" "$B"; do
    on "$s" shell dumpsys activity activities 2>/dev/null | grep -E "topResumedActivity|ResumedActivity" | head -3 >>"$OUT/raw/$label-top-activity.txt" || true
  done
  pause "[$label] Note on each phone: whose Reveal appeared? Any 'New tag collected'? Then press Back/✕ on each Reveal."
  shot "$A" "$label-A-after-dismiss"; shot "$B" "$label-B-after-dismiss"
  echo "$label: see raw/$label-*.log and screenshots/$label-*.png; fill checklist.md" >>"$OUT/raw/rounds.txt"
}

kill_kipat() { for s in "$A" "$B"; do on "$s" shell am kill "$PKG" || true; done; }

scenario_apps() {
  kill_kipat
  on "$A" shell am start -a android.intent.action.VIEW -d https://www.wikipedia.org >/dev/null || true
  on "$B" shell am start -a android.settings.SETTINGS >/dev/null || true
  touch_round "apps-chrome-settings" "A shows a browser, B shows Settings. Kipat is NOT open on either."
}

scenario_home() {
  for s in "$A" "$B"; do on "$s" shell input keyevent KEYCODE_HOME; done
  touch_round "home-home" "Both phones on the home screen."
}

scenario_killed() {
  kill_kipat
  on "$A" shell am start -a android.settings.SETTINGS >/dev/null || true
  on "$B" shell input keyevent KEYCODE_HOME
  touch_round "process-killed" "Kipat's process was killed with 'am kill' (NOT force-stop). A Settings, B home."
}

scenario_locked() {
  on "$A" shell input keyevent KEYCODE_POWER
  touch_round "a-locked" "Phone A is LOCKED (screen off or on lock screen); B unlocked. Expected: A shares nothing."
  pause "Unlock phone A, then continue."
  touch_round "a-unlocked-again" "A unlocked again: retry."
}

scenario_forcestop() {
  for s in "$A" "$B"; do on "$s" shell am force-stop "$PKG" || true; done
  touch_round "force-stopped" "Kipat was FORCE-STOPPED on both. Android 17 does not dispatch NFC to stopped apps; HCE may also be suppressed. Record what happens."
}

main() {
  local sc=("$@"); [[ ${#sc[@]} -eq 0 ]] && sc=(all)
  [[ ${sc[0]} == all ]] && sc=(info install setup apps home killed locked)
  check
  for s in "${sc[@]}"; do "scenario_$s"; done
  [[ $DRY == 1 || -f $OUT/checklist.md ]] || cp "$ROOT/docs/device-validation/_template/ambient-two-phone-checklist.md" "$OUT/checklist.md"
  log "evidence in $OUT — fill checklist.md (logs contain event names and sizes only)"
}
main "$@"
