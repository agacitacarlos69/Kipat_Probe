# Reveal surface — references

Question: how do shipped products present *a person to a recipient*, and what should the Kipat
Reveal surface learn? Sources are current product docs and coverage (2025–2026); earlier
observations are in `reference-matrix.md` (B1–B3, H1–H3, P1–P2, A1–A7). Nothing here is copied.

Common principle across all of them: **configuring a card and receiving a card are different
experiences.** HiHello has a separate Preview while editing and "mini-views" of what connections
will see; Popl and Blinq show the recipient a web card that is not the editor. Kipat already
separates Profile (configure) from the Reveal (receive); the style picker must show the
*recipient* surface, not a settings list.

## Blinq — recipient web card
Sources: [Saving a Blinq card to contacts](https://support.blinq.me/en/articles/68044-saving-a-blinq-card-to-your-mobile-contacts), [G2 discussion on recipient drop-off](https://www.g2.com/discussions/where-do-recipients-drop-off-between-opening-a-blinq-card-and-saving-the-contact), [Dupple review, Jul 2026](https://dupple.com/reviews/blinq)

- **What works:** identity first (photo, name, title, company), one-tap actions (save, website,
  LinkedIn); no account needed to save.
- **What does not:** the recipient's first screen is a pop-up (save / share back / lead capture);
  G2 users report drop-off exactly there. Android gets a browser + `.vcf` download.
- **Kipat should learn:** the first thing the recipient sees must be the person, and the save
  action must be one obvious tap.
- **Kipat must avoid:** interstitials before identity, lead capture, "share yours back" prompts.

## HiHello — recipient card and preview
Sources: [Preview your card while you edit](https://support.hihello.com/hc/en-us/articles/26520785758747), [HiHello's new look](https://hihello.com/blog/hihellos-new-look-discover-the-updated-way-to-view-business-cards), [New edit experience](https://www.hihello.com/blog/feature-launch-new-edit-experience)

- **What works:** edit and preview are separate; the cards list shows mini-views of the
  recipient-facing card, so people choose by looking, not by reading settings.
- **What does not:** templated "card" layouts with large header images and colour fields; the
  card resembles a printed business card on screen.
- **Kipat should learn:** the picker needs a **live preview** of the recipient surface; thumbnails
  alone ask people to imagine the result.
- **Kipat must avoid:** business-card proportions and decorative colour blocks.

## Popl — shared profile
Sources: [Popl digital business cards for teams](https://support.popl.co/en/articles/12293864-popl-digital-business-cards-what-s-in-it-for-your-team) (no public preview doc was found; earlier observations P1–P2)

- **What works:** teams can tailor what each audience sees — the shared card is a deliberate
  subset, not the whole profile.
- **What does not:** recipient pages grow long (links, socials, lead forms) and can put a form
  before the person.
- **Kipat should learn:** the Reveal is the *subset*: who, what, trusted, what can I do. The rest
  is behind Open profile.
- **Kipat must avoid:** bios, link lists, phone/email, galleries, badges in the Reveal.

## Android contact surfaces (Google Contacts, Phone calling cards)
Sources: [Google Contacts Material 3 Expressive rollout (Android Authority)](https://www.androidauthority.com/google-contacts-material-3-expressive-rollout-3583994), [Calling cards (MobileSyrup)](https://mobilesyrup.com/2025/08/15/google-redesigned-the-android-contact-cards-and-they-look-great/), [Contacts details redesign (Android Central)](https://www.androidcentral.com/apps-software/google-contacts-details-page-redesign)

- **What works:** picture and name above a compact row of actions; grouped tonal containers instead
  of one big white plane; monogram avatars with a tinted fill when there's no photo; the edit
  control lives elsewhere (top bar), not in the identity.
- **What does not:** calling cards let people restyle name fonts and colours — expressive, but
  inconsistent and hard to read across contacts.
- **Kipat should learn:** a designed monogram (tinted fill, confident initials), tonal grouping, and
  lightweight icon+label secondary actions are native Android vocabulary.
- **Kipat must avoid:** per-person fonts and full-colour backgrounds; the accent is a detail.

## Google account card / switcher
Sources: [Account switcher goes fullscreen (9to5Google, May 2025)](https://9to5google.com/2025/05/14/comment-fullscreen-google-account-switcher-android/), [Material You account switcher (9to5Google)](https://9to5google.com/2022/09/30/google-account-switcher-material-you/)

- **What works:** avatar + name + email as one compact header; the "who" is legible in one line of
  sight; actions are attached below with a hairline.
- **What does not:** Google itself moved away from the floating card to a fullscreen sheet —
  reviewers called the transition blunt. A floating card works only when it's compact.
- **Kipat should learn:** a row header (avatar beside name) is the fastest "who" for technical
  layouts; keep the surface compact.
- **Kipat must avoid:** a large floating card with generic dialog chrome.

## Credential presentation (Google Wallet digital ID)
Sources: [Google Wallet Digital ID](https://wallet.google/intl/en_ALL/digitalid/), [Wallet identity developer docs](https://developers.google.com/wallet/identity)

- **What works:** before sharing, the holder reviews exactly what is shared; trust is stated in
  plain words and tied to the credential, not shouted with a badge.
- **What does not:** credential UIs lean on card art (issuer imagery) that a person-to-person
  identity doesn't have.
- **Kipat should learn:** trust belongs *inside* the identity block, in words, with a state
  colour on a small glyph only. Revoked must be clear without alarm styling.
- **Kipat must avoid:** big green badges, shields, seals, "100% verified" theatre.

## Material 3 bottom sheets (for the picker decision)
Sources: [M3 bottom sheets](https://material.io/components/sheets-bottom), [Moon bottom sheet sizes](https://beta.moon.io/docs/components/containers-and-layout/bottom-sheet)

- Modal sheets suit short choices; content with a large live preview, six options, an accent row
  and two actions is a **full-height** task, where a sheet adds drag-to-dismiss conflicts with the
  inner list. Decision recorded in `design/reveal-surface/surface-scorecard.md`.

## Synthesis for the Kipat Reveal surface

1. Person first: a designed anchor (photo, logo or monogram) and the name in one relationship.
2. Context in two lines at most, organisation quieter than role.
3. Trust inside the identity block: glyph + words; detail only when something is wrong.
4. One primary action attached to the identity; secondary actions as a light icon+label row.
5. Close is quiet and always in the same corner.
6. Compact, content-driven height; no business-card proportions; tonal separation, not a
   floating white plane.
