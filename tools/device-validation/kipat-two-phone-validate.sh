#!/usr/bin/env bash
# Kipat two-phone NFC validation (docs/nfc-phone-to-phone.md).
#
# Drives the automatable parts on TWO physical phones over adb and records evidence; a person
# still does the touching and judges the result. Every adb call names a serial explicitly, so a
# command can never run on the wrong phone.
#
# Usage:
#   PRESENTER_SERIAL=<serial A> RECEIVER_SERIAL=<serial B> tools/device-validation/kipat-two-phone-validate.sh [step ...]
#   steps: info install open run swap all   (default: all = info install open run)
#   DRY_RUN=1    print the adb commands instead of running them (no phones needed)
#   APK=path     APK to install (default: app/build/outputs/apk/debug/app-debug.apk)
#   RUN_WAIT_S   seconds of logcat captured per touch (default 60)
#
# Find serials with: adb devices -l
set -euo pipefail

PKG=com.kipat.probe
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
APK=${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}
ADB=${ADB:-adb}
DRY=${DRY_RUN:-0}
WAIT_S=${RUN_WAIT_S:-60}
TAGS=(KipatPeerNfc:I KipatHce:I KipatPeerReader:I KipatPeerProtocol:I KipatReveal:I)

log() { printf '\033[1m[kipat-2p]\033[0m %s\n' "$*" >&2; }
on() { # on <serial> <adb args...>
  local s=$1; shift
  if [[ $DRY == 1 ]]; then echo "adb -s $s $*" >&2; [[ "$*" == *getprop* ]] && echo "dry"; return 0; fi
  "$ADB" -s "$s" "$@"
}
prop() { on "$1" shell getprop "$2" | tr -d '\r'; }

check_serials() {
  P=${PRESENTER_SERIAL:-}; R=${RECEIVER_SERIAL:-}
  if [[ -z $P || -z $R ]]; then log "set PRESENTER_SERIAL and RECEIVER_SERIAL (see: adb devices -l)"; exit 2; fi
  if [[ $P == "$R" ]]; then log "PRESENTER_SERIAL and RECEIVER_SERIAL must be different phones"; exit 2; fi
  if [[ $DRY == 1 ]]; then OUT=${DRY_OUT:-/tmp/kipat-two-phone-dry-run}; mkdir -p "$OUT"; return; fi
  command -v "$ADB" >/dev/null || { log "adb not found (set ADB=/path/to/adb)"; exit 2; }
  for s in "$P" "$R"; do
    "$ADB" devices | awk 'NR>1 && $2=="device"{print $1}' | grep -qx "$s" || { log "$s is not connected and authorised"; exit 2; }
    if [[ $(prop "$s" ro.kernel.qemu) == 1 || $(prop "$s" ro.boot.qemu) == 1 ]]; then log "$s is an emulator; this needs physical phones"; exit 2; fi
  done
  slug() { printf '%s-%s' "$(prop "$1" ro.product.manufacturer)" "$(prop "$1" ro.product.model)" | tr 'A-Z ' 'a-z-' | tr -cd 'a-z0-9-'; }
  OUT=$ROOT/docs/device-validation/two-phone/$(slug "$P")__$(slug "$R")
  mkdir -p "$OUT/screenshots" "$OUT/raw"
}

step_info() {
  log "device info and NFC capabilities"
  {
    echo "# Two-phone NFC — devices"
    echo
    echo "| Role | Serial | Manufacturer | Model | Android | SDK | Skin | NFC | HCE |"
    echo "|---|---|---|---|---|---|---|---|---|"
    for role in presenter receiver; do
      local s; s=$([[ $role == presenter ]] && echo "$P" || echo "$R")
      local skin; skin=$(prop "$s" ro.miui.ui.version.name)$(prop "$s" ro.build.version.oneui)$(prop "$s" ro.build.version.opporom)$(prop "$s" ro.mi.os.version.name)
      local feats; feats=$(on "$s" shell pm list features | tr -d '\r')
      local nfc=no hce=no
      grep -q 'android.hardware.nfc$' <<<"$feats" && nfc=yes
      grep -q 'android.hardware.nfc.hce' <<<"$feats" && hce=yes
      echo "| $role | \`$s\` | $(prop "$s" ro.product.manufacturer) | $(prop "$s" ro.product.model) | $(prop "$s" ro.build.version.release) | $(prop "$s" ro.build.version.sdk) | ${skin:-—} | $nfc | $hce |"
    done
  } >"$OUT/device-info.md"
  log "wrote $OUT/device-info.md"
}

step_install() {
  [[ $DRY == 1 || -f "$APK" ]] || { log "APK missing: run ./gradlew :app:assembleDebug"; exit 2; }
  for s in "$P" "$R"; do log "install on $s"; on "$s" install -r "$APK" >/dev/null; done
}

step_open() {
  log "open Share with another Kipat on both phones"
  for s in "$P" "$R"; do
    on "$s" shell am start -W -n "$PKG/.MainActivity" --es kipat.nav.destination share/nearby >/dev/null
  done
}

shot() { # shot <serial> <stem>
  if [[ $DRY == 1 ]]; then echo "adb -s $1 exec-out screencap -p > screenshots/$2.png" >&2; return; fi
  "$ADB" -s "$1" exec-out screencap -p >"$OUT/screenshots/$2.png"
}

touch_round() { # touch_round <label> <presenter serial> <receiver serial>
  local label=$1 pres=$2 recv=$3
  log "[$label] PRESENTER ($pres): tap Present my Kipat.  RECEIVER ($recv): tap Receive a Kipat."
  log "[$label] then hold the backs of the phones together until both confirm. Capturing ${WAIT_S}s of logs…"
  on "$pres" logcat -c || true
  on "$recv" logcat -c || true
  if [[ $DRY == 1 ]]; then
    echo "adb -s $pres logcat -s ${TAGS[*]}  (for ${WAIT_S}s)" >&2
    echo "adb -s $recv logcat -s ${TAGS[*]}  (for ${WAIT_S}s)" >&2
  else
    timeout "$WAIT_S" "$ADB" -s "$pres" logcat -s "${TAGS[@]}" >"$OUT/raw/$label-presenter.log" &
    timeout "$WAIT_S" "$ADB" -s "$recv" logcat -s "${TAGS[@]}" >"$OUT/raw/$label-receiver.log" &
    wait || true
  fi
  shot "$pres" "$label-presenter"
  shot "$recv" "$label-receiver"
}

step_run() { touch_round a-presents "$P" "$R"; }
step_swap() { step_open; touch_round b-presents "$R" "$P"; }

main() {
  local steps=("$@"); [[ ${#steps[@]} -eq 0 ]] && steps=(all)
  [[ ${steps[0]} == all ]] && steps=(info install open run)
  check_serials
  for st in "${steps[@]}"; do "step_$st"; done
  [[ $DRY == 1 ]] || [[ -f $OUT/checklist.md ]] || cp "$ROOT/docs/device-validation/_template/two-phone-checklist.md" "$OUT/checklist.md"
  log "evidence in $OUT — fill checklist.md; logs are in raw/ (event names and sizes only)"
}
main "$@"
