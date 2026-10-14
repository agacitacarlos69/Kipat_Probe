# NFC

Two architectures: **physical cards** (NDEF, this document and
[nfc-self-contained-profile.md](nfc-self-contained-profile.md)) and **phone to phone** (Host Card
Emulation + reader mode + APDUs, [nfc-phone-to-phone.md](nfc-phone-to-phone.md)). They share
the public profile schema and the Reveal, nothing else. Touching two phones never goes through
NDEF, and Android Beam is not used.

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

**Self-contained test profiles** (MIME `application/vnd.kipat.profile+json`, development only)
carry the whole profile on the card and need no lookup. `NfcIntentParser.parseTap` classifies a
tap as `Profile`, `Card` or `Unknown`; an embedded profile wins over a Kipat URI on the same tag.
Always shown as Unverified + "Test profile". See [nfc-self-contained-profile.md](nfc-self-contained-profile.md).

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

## Writing: full test profiles (Developer tools → NFC full profile test)

Reader mode while waiting for a card; exact size check against `Ndef.getMaxSize`; confirmation
before replacing existing data; write → close → reconnect → read back → compare, and only then
"PROFILE WRITE VERIFIED". Details in [nfc-self-contained-profile.md](nfc-self-contained-profile.md).

## Needs a physical device (not done in this environment)

- Writing and reading a full test profile on NTAG213 (expect PROFILE TOO LARGE for the sample),
  NTAG215 and NTAG216; tapping it from the launcher, over another app, cold and warm

- Writing NTAG 213/215/216 (blank, pre-formatted, read-only, too-small)
- Tapping a written card: from the home screen, from another app, with Kipat already open, with the
  writer armed, and with Kipat not installed (AAR → Play Store)
- Backdrop behaviour of the translucent Reveal on Samsung, Xiaomi, Pixel (and the OPPO baseline)
- Launch latency (Perfetto) against the ~190 ms OPPO probe baseline

## Not implemented

NTAG 424 DNA SUN/CMAC verification, backend card lookup, cryptographic peer identity for
phone-to-phone exchanges.
