# NFC

## Reading: card tap → Reveal

```
Kipat Card (NDEF: URI record + AAR)
  → Android NDEF_DISCOVERED dispatch (no App Links verification needed)
  → TapExperienceActivity (own task, above the current app)
  → NfcIntents.uriFrom(intent)  (intent data, else first NDEF URI record; API 33+ typed getters)
  → NfcIntentParser.parse       (pure Kotlin)
  → RevealResolver.resolve      (local data only — never waits on network)
  → RevealScreen
```

Accepted URIs:

| URI | Channel | Notes |
|---|---|---|
| `https://kipat.app/c/{id}` (also `/tap/`, `/p/`, `www.`) | NFC_TAG | Optional hints `n` (name), `s`, `o`, `t` (template), `th` (theme) — always shown as **Unverified** |
| `https://probe.kipat.com…` | LEGACY_PROBE_TAG | Tags written by the old probe still open the Reveal with a "re-write this tag" message |
| `kipatprobe://tap/{id}` | DEBUG_DEEP_LINK | Same hints plus `a=` authenticity override for QA; overrides are labelled demo |

Card IDs must match `[A-Za-z0-9_-]{1,64}`; anything else becomes an unresolved card.

**App Links:** the manifest does *not* claim `https://kipat.app` for `VIEW` intents and uses no
`autoVerify`, because no `assetlinks.json` is deployed. Browser links to kipat.app therefore open
the browser. NFC dispatch works regardless.

## Writing: prototype cards (Profile → Developer tools)

Migrated from the original `MainActivity.java`:

- NDEF message = URI record + Android Application Record (`com.kipat.probe`)
- `Ndef` path: connect → writable check → capacity check → write; `NdefFormatable` path: format
- `close()` in `finally` for both
- Android 13+ `getParcelableExtra(…, Tag::class.java)` with legacy fallback

Changed:

- Foreground dispatch is enabled **only while armed** and resumed (the probe enabled it on every
  resume, which swallowed all tags while the app was open). Leaving the Developer tools screen disarms.
- Writes run on `Dispatchers.IO` instead of the main thread.
- Typed results: read-only, insufficient capacity, tag lost, unsupported, format failed, I/O,
  NFC off, no NFC. Errors keep the writer armed for a retry (as before); success disarms.
- Same tag again within 2 s is ignored (duplicate scan).
- Payload changed from `https://probe.kipat.com` to `https://kipat.app/c/{cardId}` (+ optional
  short name/template hints, sized for NTAG213's 144 bytes).

## Needs a physical device (not done in this environment)

- Writing NTAG 213/215/216 (blank, pre-formatted, read-only, too-small)
- Tapping a written card: from the home screen, from another app, with Kipat already open, with the
  writer armed, and with Kipat not installed (AAR → Play Store)
- Backdrop behaviour of the translucent Reveal on Samsung, Xiaomi, Pixel (and the OPPO baseline)
- Launch latency (Perfetto) against the ~190 ms OPPO probe baseline

## Not implemented

NTAG 424 DNA SUN/CMAC verification, Host Card Emulation (phone-to-phone), backend card lookup.
