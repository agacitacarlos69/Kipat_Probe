# Production handoff

## What exists

- **Vector masters.** `../directions/<edition>/front.svg`, `back.svg` and `back-generic.svg`, plus the Mono
  monogram and dark variants. They are built by `../tools/build.py`. Units are millimetres. Every
  canvas is 91.60 × 59.98 mm (3 mm working bleed). Text is outlined Inter. Layers:
  - `WHITE_INK`
  - `CMYK`
  - `SPOT_GLOSS`
  - `DEBOSS`
  - `NFC_SAFETY_ZONE`
  - `GUIDES`
  - `MATERIAL`
- **Proofs.** These are review renders, not press files:
  - `front-proof.png` and `back-proof.png` (20 px/mm)
  - `front-finish.png`
  - `front-guides.png`

## What does not exist (deliberately)

**No press-ready PDFs.** This toolchain can make vector PDFs through headless Chromium, but they would be
RGB, without PDF/X, CMYK separations or named spot channels. Calling them print files would be
dishonest. Export them like this instead:

1. Get the chosen vendor's CR80 template: bleed, safe zone, colour profile, spot-plate naming, white-ink
   rules, and the **inlay drawing**.
2. Open the SVG in Illustrator, Affinity Designer or Inkscape, keeping the layers.
3. Set the document to the vendor's bleed and colour mode (CMYK, the vendor's ICC profile). Convert the
   reference RGB using the **intent** column in `../system/color.md`, then proof it. Do not rely on
   automatic conversion for cobalt or graphite.
4. Recolour the `SPOT_GLOSS` and `DEBOSS` keys (magenta and cyan) to 100% black on separate pages or
   layers, named as the vendor requires.
5. Replace the placeholder QR with the vendor's variable-data field, or with the per-card SVG from
   `build.py`. Keep it 16 mm with a 4-module quiet zone.
6. Replace `NFC_SAFETY_ZONE` with the inlay drawing and confirm nothing conductive sits inside it.
7. Export to PDF/X-4 (or the vendor's spec), one page per side and per plate.
8. Run `PRINT_QA.md`.

## Changing the artwork

Edit `../tools/build.py`, never the SVGs by hand, then run:

```
python3 design/card/tools/build.py    # masters, proofs, QR decode check
python3 design/card/tools/boards.py   # presentation boards
```

Requirements:
- Python 3 with `uharfbuzz`, `fonttools`, `segno`, `Pillow` and `opencv-python-headless`
- Inter (OFL) in `/usr/share/fonts/opentype/inter` (or set `KIPAT_INTER_DIR`)
- Chromium (`KIPAT_CHROME`)
