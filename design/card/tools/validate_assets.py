#!/usr/bin/env python3
"""
Sanity checks for the card artwork on disk. Read-only: it never rewrites artwork.

    python3 design/card/tools/validate_assets.py

Checks that every master SVG parses, has the CR80 canvas and viewBox, carries the expected layers,
keeps all outlined text inside the 4 mm safe zone, and has a placeholder QR on the back. It also
checks that every expected proof, board and document exists, and that every QR proof decodes
(OpenCV). It does not replace a vendor proof or a phone scan of a printed card.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CARD = Path(__file__).resolve().parent.parent
NS = {"svg": "http://www.w3.org/2000/svg"}
W, H, BLEED, SAFE = 85.60, 53.98, 3.0, 4.0
EDITIONS = ["graphite-precision", "frosted-contact", "architectural-line", "aperture", "layered-depth", "essential-mono"]
LAYERS = {"MATERIAL", "WHITE_INK", "CMYK", "SPOT_GLOSS", "DEBOSS", "NFC_SAFETY_ZONE", "GUIDES"}
PLACEHOLDER = "https://kipat.app/c/kc_0000000000000000"

errors: list[str] = []
checks = 0


def check(ok: bool, msg: str):
    global checks
    checks += 1
    if not ok:
        errors.append(msg)


def path_bbox(d: str):
    """Bounding box of a path's on-curve and control points (absolute and relative M L H V C S Q T Z)."""
    tokens = re.findall(r"[MLHVCSQTZmlhvcsqtz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    arity = {"M": 2, "L": 2, "T": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "Z": 0}
    x = y = sx = sy = 0.0
    xs, ys = [], []
    cmd, i = None, 0
    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                x, y = sx, sy
            continue
        n = arity[cmd.upper()]
        args = [float(v) for v in tokens[i:i + n]]
        i += n
        rel = cmd.islower()
        up = cmd.upper()
        if up == "H":
            x = x + args[0] if rel else args[0]
        elif up == "V":
            y = y + args[0] if rel else args[0]
        else:
            for k in range(0, n, 2):
                px = x + args[k] if rel else args[k]
                py = y + args[k + 1] if rel else args[k + 1]
                xs.append(px)
                ys.append(py)
            x, y = (x + args[-2], y + args[-1]) if rel else (args[-2], args[-1])
            if up == "M":
                sx, sy = x, y
                cmd = "l" if rel else "L"   # implicit lineto after moveto
        xs.append(x)
        ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def svg_checks(path: Path, side: str):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        check(False, f"{path}: does not parse ({e})")
        return
    check(abs(float(root.get("width").rstrip("mm")) - (W + 2 * BLEED)) < 1e-6, f"{path}: width is not trim + 2 x bleed")
    check(abs(float(root.get("height").rstrip("mm")) - (H + 2 * BLEED)) < 1e-6, f"{path}: height is not trim + 2 x bleed")
    vb = [float(v) for v in root.get("viewBox").split()]
    check(vb == [-BLEED, -BLEED, W + 2 * BLEED, H + 2 * BLEED], f"{path}: viewBox {vb}")
    ids = {g.get("id") for g in root.findall("svg:g", NS)}
    check(LAYERS <= ids, f"{path}: missing layers {LAYERS - ids}")
    cmyk = root.find("svg:g[@id='CMYK']", NS)
    texts = [p for p in cmyk.iter("{http://www.w3.org/2000/svg}path") if p.get("data-text")]
    check(bool(texts), f"{path}: no outlined text found")
    for p in texts:
        x0, y0, x1, y1 = path_bbox(p.get("d"))
        inside = x0 >= SAFE and y0 >= SAFE and x1 <= W - SAFE and y1 <= H - SAFE
        check(inside, f"{path}: text '{p.get('data-text')}' outside the safe zone ({x0:.2f},{y0:.2f})-({x1:.2f},{y1:.2f})")
    check(not cmyk.findall(".//svg:text", NS), f"{path}: live <text> found; masters must be outlined")
    if side == "back":
        qr = [p for p in cmyk.iter("{http://www.w3.org/2000/svg}path") if p.get("id") == "qr"]
        check(len(qr) == 1 and qr[0].get("data-qr") == PLACEHOLDER and qr[0].get("data-placeholder") == "true",
              f"{path}: QR must be the marked placeholder")
    if side == "front":
        dot = cmyk.find(".//svg:circle[@id='contact-point']", NS)
        check(dot is not None and abs(float(dot.get("cx")) - 64.2) < 1e-6 and abs(float(dot.get("cy")) - 26.99) < 1e-6 and float(dot.get("r")) == 1.2,
              f"{path}: contact point missing or moved")


def main():
    for key in EDITIONS:
        folder = CARD / "directions" / key
        for name in ["concept.md", "front.svg", "back.svg", "back-generic.svg", "front-proof.png", "back-proof.png",
                     "back-generic-proof.png", "front-finish.png", "front-guides.png"]:
            check((folder / name).exists(), f"missing {folder.relative_to(CARD)}/{name}")
        svg_checks(folder / "front.svg", "front")
        svg_checks(folder / "back.svg", "back")
    mono = CARD / "directions" / "essential-mono"
    for name in ["front-dark.svg", "back-dark.svg", "front-monogram.svg", "front-monogram-dark.svg"]:
        check((mono / name).exists(), f"missing essential-mono/{name}")
    svg_checks(mono / "front-monogram.svg", "front")
    for rel in ["final/card-family-board.png", "final/graphite-precision-board.png", "final/architectural-line-board.png",
                "final/frosted-contact-board.png", "final/marketing-presentation.jpg", "boards/wireframes.png",
                "boards/physical-digital-pairings.png", "boards/identity-stress.png", "directions/scorecard.md",
                "system/CARD_DESIGN_SYSTEM.md", "system/typography.md", "system/color.md", "system/finishes.md",
                "research/reference-matrix.md", "production/PRINT_QA.md", "FINAL_RECOMMENDATION.md", "higgsfield/critique.md"]:
        check((CARD / rel).exists(), f"missing {rel}")
    try:
        import cv2
        det = cv2.QRCodeDetector()
        for png in sorted((CARD / "directions").glob("*/back-proof*.png")):
            img = cv2.imread(str(png))
            check(det.detectAndDecode(img)[0] == PLACEHOLDER, f"{png.relative_to(CARD)}: QR does not decode")
    except ImportError:
        print("opencv not installed: QR decode skipped (pip install opencv-python-headless)")
    if errors:
        print(f"{len(errors)} problem(s) in {checks} checks:")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print(f"card assets ok: {checks} checks")


if __name__ == "__main__":
    main()
