# Web profile requirements (not built)

**Why:** a physical Kipat card, a QR code or a link must work for someone **without** Kipat.
`https://kipat.app/c/<id>` already opens a browser on those phones (the card's Android app record only
helps on Android). Today that page doesn't exist. This document defines what it must be. Nothing
here is implemented.

## The rule

> The recipient must learn **who the person is** without installing anything.

Installing Kipat is an offer that comes after the identity, never a gate in front of it.

## Routes

| Route | Shows |
|---|---|
| `kipat.app/c/<kc_ card id>` | The card's owner, resolved through the card contract (`docs/architecture/card-resolution-contract.md`); status checked first |
| `kipat.app/c/<public profile id>` | The same page for a profile link or QR (Share screen) |
| Disabled / revoked / unknown | Plain messages matching the app: "Kipat card unavailable", "Kipat card removed", "This Kipat link doesn't exist". No profile, no hints |

## Content (in this order)

1. **Identity:** monogram or avatar, display name (largest type on the page), headline, organisation, optional role.
2. **Trust:** "Unverified Kipat" for every NTAG card and link. "Verified" only after server-side
   NTAG 424 DNA verification. Never inferred from styling.
3. **Published fields:** website, GitHub, LinkedIn and public email, only if the owner published them.
   Shown as plain links, never auto-opened.
4. **Actions:**
   - **Save contact** (a vCard download), primary on the web.
   - **Open in Kipat** if the app is installed (Android App Link, once `assetlinks.json` is deployed).
   - **Get Kipat**, secondary: one text link, never a modal or an interstitial.
   - "Connect" exists only in the app: a Connection is an in-app relationship. The web offers "Save
     contact" and "Open in Kipat" instead.

## Presentation

- The owner's **chosen Reveal and accent** decide the page's presentation. Reuse the identity
  surface's layout and the accent on the monogram.
- A short static or CSS-only entrance is fine. It does **not** need to reproduce the native Reveal
  animation, and must respect `prefers-reduced-motion`.
- Kipat's Quiet Graphite rules apply: one filled button, sentence case, no gradients or glass, light
  and dark themes.
- It must be fast on a budget phone over mobile data: no client framework required for first paint;
  the identity is server-rendered HTML.

## Privacy and security

- Only the published projection from the card contract. Never unpublished fields, owner account
  ids, other cards, or tap history.
- No third-party trackers or ad pixels. Analytics, if any, are aggregate and cookie-less.
- `noindex` by default (people's pages shouldn't be search-indexed unless the owner opts in).
- Unknown and malformed ids look identical (no id probing). Rate-limited.
- The vCard contains only published fields.

## Out of scope for the first version

- Accounts on the web, editing a profile on the web, Connections on the web.
- Messaging, feeds, followers.
- File sharing.
- Payments or card ordering.

## Acceptance (when built)

- [ ] A card tapped on a phone without Kipat shows the owner's name within 1.5 s on 4G.
- [ ] Changing the owner's Reveal or accent in the app changes the web page on the next load (same revision as the app).
- [ ] A disabled card shows no profile.
- [ ] Save contact works on Android and iOS.
- [ ] No request to any third-party domain.
- [ ] Lighthouse accessibility ≥ 95; works with JavaScript disabled.
