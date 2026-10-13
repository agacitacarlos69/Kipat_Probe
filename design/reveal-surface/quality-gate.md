# Reveal surface — quality gate

Judged on stills of the finished surface with no animation
(`docs/images/reveal-surface-finals.png`, `docs/images/reveal-surface-states.png`, produced by
`RevealTemplateGalleryTest` and `IdentitySurfaceRenderTest`). Not yet checked on a phone.

| Question | Answer |
|---|---|
| If I screenshot only the finished modal, is it a professionally designed Kipat product? | Yes. It reads as a person with trust and actions, not a dialog: designed anchor, strong name, quiet context, trust in words, one primary action, light secondary row, quiet close |
| Is the identity obvious? | Yes: anchor and a 22–24 sp name form the first block in every layout |
| Is the organisation readable? | Yes: 14 sp secondary ink (≥ 7:1), wraps to two lines, quieter than the role |
| Is authenticity understandable? | Yes: always in words; revoked and couldn't-verify add one plain line |
| Is Connect obvious? | Yes: the only filled element |
| Is the surface too tall? | No: 280–370 dp for real content (was 367 dp with ~35% unstructured gaps) |
| Is there meaningless empty space? | No: gaps are 16 / 20 dp and each separates a group (identity / trust / actions) |
| Too many buttons? | No: one button + one or two text actions |
| Does it look like a generic form? | No: no stacked outlined buttons, no dialog title, no field-like rows |
| Does it resemble an AI mockup? | No gradients, glow, glass, pill buttons, huge radii or decorative badges; monochrome surface with one accent |
| Would a recipient understand it in two seconds? | Who (anchor + name) → what (role, organisation) → trusted (row) → what can I do (Connect) is a single top-to-bottom read |
| During motion: does a generic modal "cut in" after the animation? | No: the surface emerges from each primitive and content arrives with it (no empty card in any 25/50/75 % frame) |
| Is the final state static? | Yes: the clock stops at max(actions settled, primitive finished); Spatial Card's tilt snaps to 0 |

## Higgsfield

Not used for this round, by decision: the open questions were layout, hierarchy, type and edge
weight, which Kipat designs in Compose and judges from renders; the depth/edge reference from v3
(`design/higgsfield/references/v3/spatial-card-still-alpha.png`) still applies. No credits spent
(balance 115.5).
