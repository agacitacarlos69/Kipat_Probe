# Card resolution contract (production design, not built)

Status: **design only.** Kipat has no backend today:
- no network client
- no `INTERNET` permission
- no Supabase project or schema in this repository

Linked cards resolve through `LocalPrototypeCardResolver` on the phone that linked them (LOCAL
PROTOTYPE PARITY, `docs/nfc-card-identity-sync.md`). This document defines the contract a production
resolver must meet, so it can drop in behind the existing interface:

```kotlin
interface KipatCardResolver { suspend fun resolve(cardId: String): CardResolution }
```

## Backend decision gate

| Question | Answer today |
|---|---|
| Does a backend exist in the repo? | **No.** There is no Supabase, Firebase, Ktor, Retrofit or OkHttp, and no `INTERNET` permission |
| Add one now? | **No.** Product order: physical pilot first (`docs/device-validation/PILOT_TEST_PLAN.md`), then the resolver. Adding Supabase only because the strategy names it would build infrastructure before the tap is proven |
| What keeps the door open? | The suspend `KipatCardResolver` interface, the `CardResolution` result type, the opaque `kc_` ids, the public snapshot (`PublicKipatProfileSnapshot`) and its revision. All already exist |

## Request

```
GET /public/cards/{cardId}
Accept: application/json
```

- `cardId` must match `^kc_[0-9a-hjkmnp-tv-z]{16}$` (80 random bits). Anything else returns `404`
  without hitting storage.
- No authentication is needed: the card is public by design, and this endpoint returns only what the
  owner published.
- `ETag` / `If-None-Match` on the profile revision, for cheap repeat taps.
- A future NTAG 424 DNA card appends SUN parameters (`?e=<picc>&c=<cmac>`). The server verifies them
  and reports the result as `authenticity`.

## Response (200)

```json
{
  "card": {
    "id": "kc_0123456789abcdef",
    "status": "active",
    "type": "ndef_uri",
    "authenticityCapability": "none"
  },
  "identity": {
    "id": "KPT-…",
    "revision": 42,
    "type": "person",
    "displayName": "Vash Puno",
    "headline": "BS Computer Science",
    "organization": "Polytechnic University of the Philippines",
    "role": null,
    "avatarUrl": null,
    "links": { "website": null, "github": null, "linkedin": null, "publicEmail": null },
    "reveal": "precision_orbit",
    "accent": "cobalt"
  },
  "authenticity": "unverified"
}
```

| Field | Rule |
|---|---|
| `card.status` | `active`, `disabled` or `revoked`. **Checked before any identity data is read.** For `disabled` / `revoked` the response holds `card` only (no `identity`) |
| `identity` | Exactly the owner's **published** fields, the same set as `PublicKipatProfileSnapshot` and the Kipat Tap profile (one public schema, `nfc/profile/NfcProfile.kt`) |
| `identity.revision` | Monotonic; the client uses it so a Connection snapshot is never overwritten by an older one (`ConnectionRules.refreshed`) |
| `reveal`, `accent` | Stable ids (`contact_ripple` … `minimal_fade`; accent wire ids). Unknown ids fall back to the default on the client |
| `authenticity` | `unverified` for every NTAG URI card. `authentic` only after server-side SUN or CMAC verification (NTAG 424 DNA). Never inferred from styling |

## Never returned

- The owner's account or auth UUID, or any internal user id. `identity.id` is the public profile id only.
- Unpublished profile fields, the private email or phone, or contacts.
- Tokens, session data, signing keys, or administrative or audit fields.
- Other cards owned by the same identity, or card counts.
- Tap history or `lastSeenAt` of the card.

## Errors

| Case | Response |
|---|---|
| Malformed id | `404` (same body as unknown) |
| Unknown id | `404` |
| Revoked | `200` with `card.status = "revoked"`, no identity (the client shows "Kipat card removed") |
| Disabled | `200` with `card.status = "disabled"`, no identity |
| Rate limited | `429` with `Retry-After` |

Unknown and malformed answers must be identical, so card ids can't be probed. Rate-limit per client
and globally. Log only the masked id (`••••B871`) and the status, never profile values.

## Client mapping (already implemented locally)

| Response | `CardResolution` | Reveal |
|---|---|---|
| 200 active + identity | `Resolved(card, snapshot)` | The owner's current Reveal, Unverified |
| 200 disabled | `Disabled` | "Kipat card unavailable" |
| 200 revoked | `Revoked` | "Kipat card removed" |
| 404 | `Unknown` | "This card can't be opened on this phone yet" |
| Network failure / timeout (≤ 1.5 s) | `ProfileUnavailable` (or a cached snapshot labelled with its revision) | No stale profile presented as current |

## Ownership and linking (server side, later)

- Linking a card creates `(cardId, ownerIdentityId, status, createdAt)` server-side, under the owner's
  authentication.
- A `kc_` id that already exists for another owner is refused. That matches today's local
  `CardLinkPlan` (`LinkedElsewhere`).
- Factory-encoded (pre-assigned) ids need a **claim** flow before generic inventory with printed QR
  codes can ship (`design/card/system/CARD_DESIGN_SYSTEM.md`, personalisation section).

## If Supabase is chosen later (sketch, not created)

- Tables:
  - `cards(id text pk, owner_identity uuid, status text, created_at, type, authenticity_capability)`
  - `public_profiles(identity_public_id text pk, revision bigint, profile jsonb, updated_at)`
- The endpoint is a single edge function or an RPC that reads `cards` by id, checks status, then
  reads `public_profiles`.
- RLS denies direct table reads to anonymous users. Only the function returns the public projection.
- The service-role key never ships in the app and is never committed.
