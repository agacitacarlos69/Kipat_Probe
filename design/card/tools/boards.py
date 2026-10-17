#!/usr/bin/env python3
"""
Presentation boards for the Kipat card family, composed from the build.py proofs (run build.py first).

    python3 design/card/tools/boards.py

Boards are HTML laid out with Inter and rendered by headless Chromium. They are presentation only;
production artwork stays in directions/*/front.svg and back.svg.
"""
from __future__ import annotations

import html
from pathlib import Path

from PIL import Image

import build

CARD = build.CARD
REPO = build.REPO
DIRS = CARD / "directions"
BOARDS = CARD / "boards"
FINAL = CARD / "final"
CROPS = BOARDS / "reveal-crops"

EDITIONS = [
    # key, folder suffix, material, finish, complexity
    ("graphite-precision", "", "Soft-touch PVC, graphite", "Raised gloss orbit", "Medium"),
    ("frosted-contact", "", "Frosted translucent PVC", "White ink", "Medium–high"),
    ("architectural-line", "", "Matte PVC, paper white", "Raised gloss line", "Low–medium"),
    ("aperture", "", "Matte PVC, stone", "Spot gloss segments", "Low–medium"),
    ("layered-depth", "", "Soft-touch PVC, charcoal", "Deboss or gloss plane", "Medium–high"),
    ("essential-mono", "", "Matte PVC, white (or soft black)", "None", "Low"),
]
REVEAL_ROW = {"frosted-contact": 0, "graphite-precision": 1, "architectural-line": 2, "aperture": 3, "layered-depth": 4, "essential-mono": 5}
FINALISTS = ["graphite-precision", "architectural-line", "frosted-contact"]
ROLE = {"graphite-precision": "Primary", "architectural-line": "Secondary", "frosted-contact": "Experimental"}

FONT = build.FONT_DIR
CSS = f"""
@font-face {{ font-family: Inter; font-weight: 400; src: url(file://{FONT}/Inter-Regular.otf); }}
@font-face {{ font-family: Inter; font-weight: 500; src: url(file://{FONT}/Inter-Medium.otf); }}
html, body {{ margin: 0; background: #EDEDEA; color: #17181C; font-family: Inter, sans-serif; }}
.page {{ padding: 72px 80px; }}
h1 {{ font-weight: 500; font-size: 40px; letter-spacing: -0.01em; margin: 0 0 8px; }}
.sub {{ color: #565961; font-size: 20px; margin: 0 0 48px; }}
.label {{ font-weight: 500; font-size: 20px; margin: 16px 0 4px; }}
.note {{ color: #565961; font-size: 16px; line-height: 1.45; }}
img {{ display: block; }}
"""


def title(key: str) -> str:
    return build.ED[key].title


def img(path: Path, width: int) -> str:
    return f'<img src="file://{path}" width="{width}">'


def render(name: Path, body: str, w: int, h: int):
    page = f"<!doctype html><html><head><meta charset='utf-8'><style>{CSS}</style></head><body>{body}</body></html>"
    build.shoot(page, name, w, h)
    im = Image.open(name)
    # Trim the empty ground below the content (window height is generous on purpose).
    bg = im.getpixel((im.width - 2, im.height - 2))
    rows = [y for y in range(im.height) if any(abs(c - b) > 3 for c, b in zip(im.getpixel((im.width // 2, y))[:3], bg[:3])) or
            any(abs(c - b) > 3 for c, b in zip(im.getpixel((im.width // 4, y))[:3], bg[:3]))]
    if rows:
        im.crop((0, 0, im.width, min(im.height, rows[-1] + 72))).save(name)


def reveal_crops():
    """Two frames per Reveal template from the committed gallery render (docs/images/reveal-templates-light.png):
    the template's characteristic moment and its settled identity surface."""
    CROPS.mkdir(parents=True, exist_ok=True)
    src = Image.open(REPO / "docs/images/reveal-templates-light.png").convert("RGB")
    cols = [(224, 444), (896, 1116)]
    rh = src.height / 6
    out = {}
    for key, row in REVEAL_ROW.items():
        y0, y1 = round(row * rh) + 1, round((row + 1) * rh) - 3
        paths = []
        for i, (x0, x1) in enumerate(cols):
            p = CROPS / f"{key}-{'moment' if i == 0 else 'settled'}.png"
            src.crop((x0, y0, x1, y1)).resize(((x1 - x0) * 2, (y1 - y0) * 2), Image.LANCZOS).save(p)
            paths.append(p)
        out[key] = paths
    return out


def wireframes():
    cells = "".join(
        f"<div>{img(DIRS / k / 'front-guides.png', 700)}<div class='label'>{title(k)}</div>"
        f"<div class='note'>{html.escape(open(DIRS / k / 'concept.md').read().split('> **')[1].split('**')[0])}</div></div>"
        for k, *_ in EDITIONS
    )
    body = (f"<div class='page'><h1>Six editions on one grid</h1><p class='sub'>Guides on: bleed (red), trim, 4 mm safe zone, "
            f"6 mm margin, contact axes, NFC safety zone (whole card until the inlay drawing exists).</p>"
            f"<div style='display:grid;grid-template-columns:repeat(3,700px);gap:48px 56px'>{cells}</div></div>")
    render(BOARDS / "wireframes.png", body, 2400, 3000)


def pairings(crops):
    rows = ""
    for k, *_ in EDITIONS:
        m, s = crops[k]
        rows += (f"<div style='display:grid;grid-template-columns:720px 440px 440px 1fr;gap:40px;align-items:center;margin-bottom:40px'>"
                 f"{img(DIRS / k / 'front-proof.png', 720)}{img(m, 440)}{img(s, 440)}"
                 f"<div><div class='label' style='margin-top:0'>{title(k)}</div><div class='note'>Recommended Reveal: {build.ED[k].reveal}<br>"
                 f"Printed card is static. Any Reveal can be chosen in the app.</div></div></div>")
    body = (f"<div class='page'><h1>Physical card, matching Reveal</h1><p class='sub'>One visual vocabulary across object and screen. "
            f"The pairing is a recommendation, never a lock. Reveal frames: the app's template gallery render (sample data).</p>{rows}</div>")
    render(BOARDS / "physical-digital-pairings.png", body, 2400, 4200)


def identity_stress():
    rows = ""
    for k in FINALISTS + ["essential-mono"]:
        cells = "".join(f"{img(BOARDS / 'identities' / f'{k}-{i.key}.png', 520)}" for i in build.IDENTITIES)
        rows += f"<div class='label'>{title(k)}</div><div style='display:grid;grid-template-columns:repeat(4,520px);gap:24px;margin-bottom:32px'>{cells}</div>"
    body = (f"<div class='page'><h1>One back, four identities</h1><p class='sub'>Student, professional, creator, organisation. "
            f"Long names step down from 11 to 9 pt before wrapping.</p>{rows}</div>")
    render(BOARDS / "identity-stress.png", body, 2400, 4200)


def finalist_boards():
    for k in FINALISTS:
        e = build.ED[k]
        mat = next(m for kk, _, m, *_ in EDITIONS if kk == k)
        fin = next(f for kk, _, _, f, _ in EDITIONS if kk == k)
        body = (f"<div class='page'><h1>{e.title}</h1><p class='sub'>{ROLE[k]} finalist. {html.escape(open(DIRS / k / 'concept.md').read().split('> **')[1].split('**')[0])}</p>"
                f"<div style='display:grid;grid-template-columns:1080px 1080px;gap:48px'>"
                f"<div>{img(DIRS / k / 'front-proof.png', 1080)}<div class='label'>Front</div></div>"
                f"<div>{img(DIRS / k / 'back-proof.png', 1080)}<div class='label'>Back</div></div>"
                f"<div>{img(DIRS / k / 'front-finish.png', 1080)}<div class='label'>Finish plates</div><div class='note'>Magenta: gloss. Cyan: deboss. {fin}.</div></div>"
                f"<div>{img(DIRS / k / 'back-generic-proof.png', 1080)}<div class='label'>Generic back (inventory)</div><div class='note'>{mat}. Recommended Reveal: {e.reveal}.</div></div>"
                f"</div></div>")
        render(FINAL / f"{k}-board.png", body, 2400, 3000)


def family_board():
    head = "".join(f"<div class='note' style='font-weight:500;color:#17181C'>{h}</div>" for h in ["Front", "Back", "Material", "Finish", "Reveal pairing", "Complexity"])
    rows = ""
    for k, _, mat, fin, cx in EDITIONS:
        rows += (f"<div style='display:contents'>{img(DIRS / k / 'front-proof.png', 560)}{img(DIRS / k / 'back-proof.png', 560)}"
                 f"<div><div class='label' style='margin-top:0'>{title(k)}</div><div class='note'>{mat}</div></div>"
                 f"<div class='note'>{fin}</div><div class='note'>{build.ED[k].reveal}</div><div class='note'>{cx}</div></div>")
    body = (f"<div class='page'><h1>Kipat card family</h1><p class='sub'>Six editions. One contact point, one back.</p>"
            f"<div style='display:grid;grid-template-columns:560px 560px 300px 240px 200px 160px;gap:28px 32px;align-items:center'>{head}{rows}</div></div>")
    render(FINAL / "card-family-board.png", body, 2400, 4200)


def marketing(crops):
    hero = CARD / "higgsfield/photoshoots/graphite-precision-front-back-hero.jpg"
    if not hero.exists():
        return
    m, s = crops["graphite-precision"]
    body = (f"<div style='display:grid;grid-template-columns:1400px 1fr;min-height:1000px;background:#E6E5E1'>"
            f"<img src='file://{hero}' width='1400' style='height:930px;object-fit:cover'>"
            f"<div style='padding:120px 72px'><div style='font-weight:500;font-size:64px;letter-spacing:-0.02em;line-height:1.1'>Tap.<br>Know me.<br>Connect.</div>"
            f"<p class='note' style='font-size:22px;margin-top:32px;max-width:520px'>Hold a Kipat card to a phone. The person appears, in their own Reveal. "
            f"Choose to connect.</p>"
            f"<div style='display:flex;gap:16px;align-items:center;margin-top:56px'>{img(DIRS / 'graphite-precision' / 'front-proof.png', 300)}"
            f"<div style='font-size:28px;color:#565961'>→</div>{img(s, 220)}</div>"
            f"<p class='note' style='margin-top:12px'>Physical card, then the Kipat Reveal.</p></div></div>"
            f"<p class='note' style='padding:16px 24px;margin:0'>Validation mockup only, not a website. Product photo is a Higgsfield reference of the vector artwork (see critique.md); the line is under review in the positioning work.</p>")
    out = FINAL / "marketing-presentation.png"
    render(out, body, 2400, 1150)
    Image.open(out).convert("RGB").save(FINAL / "marketing-presentation.jpg", quality=86, optimize=True)  # photo content: JPG
    out.unlink()


if __name__ == "__main__":
    BOARDS.mkdir(exist_ok=True)
    FINAL.mkdir(exist_ok=True)
    crops = reveal_crops()
    wireframes()
    pairings(crops)
    identity_stress()
    finalist_boards()
    family_board()
    marketing(crops)
    print("boards ok")
