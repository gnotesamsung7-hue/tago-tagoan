#!/usr/bin/env python3
"""Stamp the app version (from app/build.gradle) onto the launcher icons.
Reads clean icons from app/icon-src/ and writes stamped copies into app/src/main/res/.
Runs automatically in the GitHub Actions build, so every APK's icon shows its own version."""
import re, os, sys
from PIL import Image, ImageDraw, ImageFont
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
gradle = open(os.path.join(ROOT, 'app', 'build.gradle'), encoding='utf-8').read()
m = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", gradle)
ver = m.group(1) if m else '?'
label = 'v' + (ver[:-2] if ver.endswith('.0') else ver)
FONT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'fonts', 'Baloo2.ttf')
def font(px):
    try:
        f = ImageFont.truetype(FONT, px)
        try: f.set_variation_by_axes([800])
        except Exception: pass
        return f
    except Exception:
        return ImageFont.load_default()
def badge(im, cx, cy, w, h):
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    r = h / 2
    sh = max(1, h * .08)
    d.rounded_rectangle((x0, y0 + sh, x1, y1 + sh), r, fill=(0, 0, 0, 120))
    d.rounded_rectangle((x0, y0, x1, y1), r, fill=(255, 200, 87, 255), outline=(40, 24, 10, 255), width=max(1, int(h * .09)))
    size = int(h * .78)
    while size > 6:
        f = font(size); bb = d.textbbox((0, 0), label, font=f)
        if bb[2] - bb[0] <= w * .82: break
        size -= 1
    bb = d.textbbox((0, 0), label, font=f)
    d.text((cx - (bb[0] + bb[2]) / 2, cy - (bb[1] + bb[3]) / 2), label, font=f, fill=(40, 24, 10, 255))
n = 0
for dpi in ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']:
    src = os.path.join(ROOT, 'app', 'icon-src', 'mipmap-' + dpi)
    dst = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'mipmap-' + dpi)
    for name in ['ic_launcher', 'ic_launcher_round', 'ic_launcher_foreground']:
        p = os.path.join(src, name + '.png')
        if not os.path.exists(p): continue
        im = Image.open(p).convert('RGBA'); S = im.size[0]
        if name == 'ic_launcher_foreground':   # adaptive icon: keep inside the 66/108 safe zone
            badge(im, S * .5, S * .70, S * .46, S * .17)
        elif name == 'ic_launcher_round':
            badge(im, S * .5, S * .80, S * .56, S * .24)
        else:
            badge(im, S * .62, S * .82, S * .66, S * .28)
        im.save(os.path.join(dst, name + '.png')); n += 1
print(f'Stamped {label} on {n} launcher icons')
