#!/usr/bin/env python3
"""
Builds KeyLingo's Android icon assets from the approved master logo
(tools/branding/keylingo-logo-master.webp). The artwork is used as supplied;
nothing is redrawn.

Outputs (app/src/main/res):
  mipmap-<density>/ic_launcher_background.png  adaptive-icon layer, 108dp: the logo's
      rounded-square artwork scaled so the square fills the central 72dp (the area
      launchers show); its transparent corners and the 18dp bleed are filled by
      smoothly extending the edge colours, so no launcher mask shows a seam.
  mipmap-<density>/ic_launcher_monochrome.png  themed-icon layer (Android 13+): the white
      K-and-bubble artwork extracted from the logo as an alpha silhouette.
  drawable-nodpi/ic_keylingo_mark.png          the rounded-square logo as supplied (in-app mark).
  ../ic_launcher-playstore.png                 512 px store icon.

Usage: python3 -I tools/branding/make_launcher_assets.py
Needs numpy and Pillow.
"""
import os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def load_master():
    im = Image.open(os.path.join(HERE, "keylingo-logo-master.webp")).convert("RGBA")
    a = np.asarray(im).astype(np.float64) / 255.0
    ys, xs = np.where(a[..., 3] > 0.5)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    side = max(x1 - x0 + 1, y1 - y0 + 1)
    cx, cy = (x0 + x1 + 1) / 2.0, (y0 + y1 + 1) / 2.0
    left, top = int(round(cx - side / 2)), int(round(cy - side / 2))
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(im.crop((left, top, left + side, top + side)), (0, 0))
    return square  # the rounded-square logo, centred on a square transparent canvas


def push_pull_fill(rgb, weight):
    """Fill pixels with weight 0 by smoothly extending known colours (image pyramid)."""
    levels = [(rgb * weight[..., None], weight)]
    while min(levels[-1][1].shape) > 2:
        c, w = levels[-1]
        h, wd = (c.shape[0] // 2) * 2, (c.shape[1] // 2) * 2
        c, w = c[:h, :wd], w[:h, :wd]
        c2 = c.reshape(h // 2, 2, wd // 2, 2, 3).sum((1, 3))
        w2 = w.reshape(h // 2, 2, wd // 2, 2).sum((1, 3))
        levels.append((c2, w2))
    c, w = levels[-1]
    filled = c / np.maximum(w, 1e-9)[..., None]
    for c, w in reversed(levels[:-1]):
        up = np.repeat(np.repeat(filled, 2, 0), 2, 1)
        up = np.pad(up, ((0, c.shape[0] - up.shape[0]), (0, c.shape[1] - up.shape[1]), (0, 0)), mode="edge")
        known = c / np.maximum(w, 1e-9)[..., None]
        alpha = np.clip(w, 0, 1)[..., None]
        filled = known * alpha + up * (1 - alpha)
    return filled


def adaptive_background(square, out_px):
    """108dp layer: the logo square fills the central 72dp; corners and bleed extended smoothly."""
    inner = int(round(out_px * 72 / 108))
    sq = np.asarray(square.resize((inner, inner), Image.LANCZOS)).astype(np.float64) / 255.0
    canvas = np.zeros((out_px, out_px, 3)); weight = np.zeros((out_px, out_px))
    o = (out_px - inner) // 2
    canvas[o:o + inner, o:o + inner] = sq[..., :3]
    # Trust only clearly opaque logo pixels; anti-aliased edge pixels are re-derived.
    weight[o:o + inner, o:o + inner] = np.clip((sq[..., 3] - 0.9) / 0.1, 0, 1)
    filled = push_pull_fill(canvas, weight)
    a = np.clip(sq[..., 3], 0, 1)[..., None]
    region = filled[o:o + inner, o:o + inner]
    filled[o:o + inner, o:o + inner] = sq[..., :3] * a + region * (1 - a)  # original wherever the logo is opaque
    return Image.fromarray((np.clip(filled, 0, 1) * 255).round().astype(np.uint8), "RGB")


def monochrome(square, out_px):
    """Themed-icon silhouette: the white K and speech bubble (high minimum channel = white)."""
    inner = int(round(out_px * 72 / 108))
    sq = np.asarray(square.resize((inner, inner), Image.LANCZOS)).astype(np.float64) / 255.0
    whiteness = sq[..., :3].min(-1)
    alpha = np.clip((whiteness - 0.45) / (0.85 - 0.45), 0, 1) * np.clip(sq[..., 3], 0, 1)
    out = np.zeros((out_px, out_px, 4))
    o = (out_px - inner) // 2
    out[o:o + inner, o:o + inner, :3] = 1.0
    out[o:o + inner, o:o + inner, 3] = alpha
    return Image.fromarray((out * 255).round().astype(np.uint8), "RGBA")


def main():
    square = load_master()
    for name, scale in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{name}"); os.makedirs(d, exist_ok=True)
        px = int(round(108 * scale))
        adaptive_background(square, px).save(os.path.join(d, "ic_launcher_background.png"), optimize=True)
        monochrome(square, px).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
    d = os.path.join(RES, "drawable-nodpi"); os.makedirs(d, exist_ok=True)
    square.resize((320, 320), Image.LANCZOS).save(os.path.join(d, "ic_keylingo_mark.png"), optimize=True)
    square.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "app", "src", "main", "ic_launcher-playstore.png"), optimize=True)
    print("wrote launcher layers for", ", ".join(DENSITIES), "+ ic_keylingo_mark.png + ic_launcher-playstore.png")


if __name__ == "__main__":
    main()
