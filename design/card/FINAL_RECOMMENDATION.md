# Kipat card: final recommendation

**Status:** design complete as vector artwork. **No physical card has been printed or NFC-tested yet.**
Nothing below claims physical validation.

## Launch three designs, not six

| Launch slot | Edition | Tier | Why |
|---|---|---|---|
| **Essential** | **Essential Mono**, white (soft-black variant at the same tier) | Kipat Essential: matte PVC, one ink | Timeless, the cheapest and the most reliable. It is the only edition that can be personalised on the front (monogram) without hurting inventory. It works for every segment, from student to executive. The likely volume seller |
| **Signature** | **Graphite Precision** | Kipat Signature: soft-touch PVC plus raised gloss | The highest desirability in the scorecard and the clearest physical echo of Kipat's contact motif (the app mark is a dot with orbit arcs). It photographs well even plainly, and uses proven PVC finishes (Dot ships soft-touch plus gloss). Dark, but tonal: it is not "black card with blue neon" |
| **Third** | **Architectural Line** | Kipat Essential (optional raised gloss) | The strongest light design and the top subtotal. Print risk is low. It covers organisations and professionals who don't want a dark card, and it keeps the launch family from becoming "the black NFC card brand" |

### Why not Frosted Contact at launch, given the hypothesis Essential / Signature / Frost

Frosted Contact is the most desirable *object* in the set. It was the strongest "ano 'yan?"
reference: the fingers showing through the card in the tap moment. But it is the only edition whose
look depends on an unproven stock build:
- the frost quality
- white-ink opacity
- whether the antenna coil shows through
- a new vendor capability, and a separate white plate

Launching it before a stock sample risks the brand's first impression. **Recommendation: the Frost tier
is the first post-launch edition, released when a frosted sample passes PRINT_QA rows 19–31.** If a
vendor proves frosted PVC before launch, swap it in for Architectural Line. It would then be the
material-led launch card.

## Tiers (concept only; no pricing, no commerce)

| Tier | What changes | Editions |
|---|---|---|
| Kipat Essential | Matte PVC, 1–2 inks, no special stock | Essential Mono (light and dark), Architectural Line, Aperture |
| Kipat Signature | Soft-touch plus one raised gloss detail; optional cobalt core at the edge (vendor-dependent) | Graphite Precision, later Layered Depth |
| Kipat Frost | Frosted translucent stock plus white ink | Frosted Contact |

There are three tiers at most. There are no metal, gold or "24k" SKUs, which research shows are the
category's clichés and an NFC risk.

## Relative production complexity (no invented prices)

| Edition | Processes | Complexity |
|---|---|---|
| Essential Mono | 1 ink + matte | **Low** |
| Architectural Line | 2 inks + matte (+ optional raised gloss) | **Low–medium** |
| Aperture | 2 inks + matte + flat gloss | **Low–medium** |
| Graphite Precision | 3 inks + soft-touch + raised gloss | **Medium** |
| Frosted Contact | Special stock + white plate + 2 inks | **Medium–high** |
| Layered Depth | 3 inks + soft-touch + deboss (or gloss) | **Medium–high** |

Actual cost requires supplier quotes, so no peso figures are given here.

## Future and limited editions (documented, not designed)

These stay inside the system: the same contact point, lockup and back. Only the front motif or the
stock changes.

- **Aperture.** Ready as artwork. Release after real-world feedback confirms it doesn't read as a camera brand.
- **Layered Depth.** Signature tier, once a vendor proves a clean PVC deboss that clears the chip.
- **University edition.** An Essential base with the university's name *on the back context line only*. Never the school crest on the front.
- **Developer, Creator or Organisation editions.** A curated stock or ink choice within the six, not new graphics.
- **Event edition.** A limited run of an existing edition with a small event line on the back.
- **Artist collaboration.** One commissioned front motif per artist, which must still keep the contact point and lockup.

No skins, characters, gradients, holographics or uploaded art.

## Generic inventory versus personalised

The full comparison is in `system/CARD_DESIGN_SYSTEM.md`. **Launch with generic inventory cards**:
- **Front:** the edition design.
- **Back:** the lockup plus "Tap to connect".
- **Profile:** fully digital, linked in the app (works today, with no app change).

Offer a personalised back (name and context) printed on demand. Add the per-card QR only with the
backend, web profile and a claim flow. The current link flow correctly refuses unknown `kc_` ids, so a
pre-assigned card id needs a claim flow first.

## Desirability tests

| Question | Essential Mono | Graphite Precision | Architectural Line | Frosted Contact |
|---|---|---|---|---|
| Wanted without mentioning NFC? | Yes, as a well-made object | Yes | Yes | Yes, the most |
| Would a student carry it? | Yes | Yes | Yes | Yes, the most |
| Would a founder hand it out? | Yes | Yes, the most | Yes | Maybe (reads younger) |
| Would a designer call it tasteful? | Yes | Yes | Yes | Yes |
| Usable in a professional meeting? | Yes | Yes | Yes | Yes |
| Still good in three years? | Yes | Yes | Yes | Probably (material trends move) |
| "Ang ganda nung card mo, ano yan?" | Sometimes (restraint) | Yes (touch the orbit) | Sometimes | Yes (light through it) |

Curiosity comes from composition, material and touch, never from loud graphics.

## Design assessment

- **Six versions of one Kipat brand?** Yes: the same contact point, lockup position and back architecture.
- **Any still looks AI-generated?** No, in the vector artwork. Two Higgsfield *references* (the
  Architectural Line still and the tap moment's generic phone) show model tells, and are classified in
  `higgsfield/critique.md`. None is production art.
- **Still desirable without NFC?** Yes for the four launch candidates. Layered Depth depends most on
  its finish, which is why it is not a launch design.

## Marketing language (around the product, never on the card)

Lead with the interaction, not the technology. The validation mockup
(`final/marketing-presentation.jpg`) uses "Tap. Know me. Connect." The parallel product-strategy
work is still evaluating that line against the app's existing "Tap. Know who I am. Connect." The
card artwork contains no slogan, so either line works with the cards unchanged.

## Next milestone

Order **physical pre-production samples** of Essential Mono, Graphite Precision and Architectural Line,
plus one frosted stock sample, from one CR80 NFC vendor with an NTAG215 inlay drawing. Run the full
`production/PRINT_QA.md`, including the NFC tap matrix, before approving any edition.
