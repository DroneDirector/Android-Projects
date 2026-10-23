"""
AlcorCut Pro launcher icon: one geometry definition -> Android adaptive-icon vector XML + PNG preview.
Usage: python3 make_icon.py <res_dir> <preview_png>
"""
import math, sys, os
import numpy as np
from PIL import Image, ImageDraw

C = 54.0  # centre of the 108x108 adaptive-icon canvas


def polar(r, deg):
    a = math.radians(deg)
    return (C + r * math.cos(a), C + r * math.sin(a))


def circle_pts(cx, cy, r, n=180):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


def arc_pts(r, a0, a1, n=24):
    return [polar(r, a0 + (a1 - a0) * i / n) for i in range(n + 1)]


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def circle_path(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z"


def poly_path(pts):
    return "M" + "L".join(f"{f(x)},{f(y)}" for x, y in pts) + "Z"


# ---------------------------------------------------------------- shape list
# Each shape: dict(path=svg-ish path for VectorDrawable, pts=polygon for preview,
#                  fill=("solid", argb) | ("linear", x0,y0,x1,y1,[(off,argb)]) | ("radial", cx,cy,r,[(off,argb)]),
#                  stroke=(argb, width) optional, cap="round")

def solid(c):
    return ("solid", c)


def linear(x0, y0, x1, y1, stops):
    return ("linear", x0, y0, x1, y1, stops)


def radial(cx, cy, r, stops):
    return ("radial", cx, cy, r, stops)


def circle(cx, cy, r, fill):
    return dict(path=circle_path(cx, cy, r), pts=circle_pts(cx, cy, r), fill=fill)


def poly(pts, fill, stroke=None):
    return dict(path=poly_path(pts), pts=pts, fill=fill, stroke=stroke)


background = [
    dict(path="M0,0h108v108h-108z", pts=[(0, 0), (108, 0), (108, 108), (0, 108)],
         fill=radial(38, 30, 96, [(0, 0xFF3B2F10), (0.45, 0xFF15130F), (1, 0xFF060607)])),
    # faint diagonal light beam
    poly([(0, 18), (30, 0), (108, 70), (108, 96)], solid(0x0CFFD27A)),
]

fg = []
# warm glow halo behind the lens
fg.append(circle(C, C, 42.0, radial(C, C, 42.0, [(0, 0x00FFB300), (0.62, 0x00FFB300), (0.72, 0x40FFB300), (1, 0x00FFB300)])))
# soft shadow under the lens
fg.append(circle(C + 0.8, C + 2.2, 32.2, solid(0x33000000)))
fg.append(circle(C + 0.4, C + 1.2, 31.4, solid(0x55000000)))
# knurled focus ring
fg.append(circle(C, C, 30.6, linear(30, 26, 80, 84, [(0, 0xFFFFEDB0), (0.35, 0xFFFFC21F), (0.7, 0xFFF29400), (1, 0xFF9A5600)])))
for i in range(40):
    a = i * 9
    w = 1.15
    p1 = polar(27.9, a - w); p2 = polar(30.2, a - w * 0.85); p3 = polar(30.2, a + w * 0.85); p4 = polar(27.9, a + w)
    fg.append(poly([p1, p2, p3, p4], solid(0xA0402600)))
# dark gap, inner gold bezel, glass
fg.append(circle(C, C, 27.4, linear(30, 30, 78, 78, [(0, 0xFF2E2003), (1, 0xFF0E0900)])))
fg.append(circle(C, C, 26.4, linear(76, 80, 32, 28, [(0, 0xFFFFD54F), (0.55, 0xFFE89400), (1, 0xFF8A4F00)])))
fg.append(circle(C, C, 25.0, radial(46, 44, 30, [(0, 0xFF26323F), (0.55, 0xFF0B0F15), (1, 0xFF030405)])))

# six-blade aperture iris (pinwheel), each blade shaded differently
RO, RI = 23.4, 7.6
shades = [(0xFFFFD456, 0xFFC27800), (0xFFFFC53A, 0xFFB06A00), (0xFFF8AE12, 0xFF9C5C00),
          (0xFFEC9C00, 0xFF8A4F00), (0xFFF6AA10, 0xFF9C5C00), (0xFFFFC02E, 0xFFB06A00)]
for k in range(6):
    th = 60 * k - 90
    outer = arc_pts(RO, th - 22, th + 38)
    h1 = polar(RI, th + 90 - 22)
    h0 = polar(RI, th + 30 - 22)
    pts = outer + [h1, h0]
    mid_out = polar(RO, th + 8)
    mid_in = polar(RI, th + 38)
    p1, p2 = outer[0], outer[-1]
    path = (f"M{f(p1[0])},{f(p1[1])}A{f(RO)},{f(RO)} 0 0,1 {f(p2[0])},{f(p2[1])}"
            f"L{f(h1[0])},{f(h1[1])}L{f(h0[0])},{f(h0[1])}Z")
    light, dark = shades[k]
    fg.append(dict(path=path, pts=pts,
                   fill=linear(mid_out[0], mid_out[1], mid_in[0], mid_in[1], [(0, light), (1, dark)]),
                   stroke=(0xE0140C00, 0.7)))
# sensor glow in the opening
fg.append(circle(C, C, 5.2, radial(C, C, 5.2, [(0, 0x66FFB300), (1, 0x00FFB300)])))
fg.append(circle(C, C, 1.2, solid(0xAAFFE7A0)))
# glass reflections
refl = arc_pts(21.0, 196, 252, 30) + list(reversed(arc_pts(17.6, 204, 246, 30)))
fg.append(poly(refl, solid(0x38FFFFFF)))
fg.append(circle(41.6, 40.6, 1.5, solid(0x99FFFFFF)))
fg.append(circle(67.5, 69.0, 0.9, solid(0x55FFFFFF)))
# star glint on the ring
sx, sy = polar(30.4, -132)
L, s = 5.2, 0.9
star = [(sx, sy - L), (sx + s, sy - s), (sx + L, sy), (sx + s, sy + s), (sx, sy + L), (sx - s, sy + s), (sx - L, sy), (sx - s, sy - s)]
fg.append(poly(star, solid(0xF2FFFFFF)))
fg.append(circle(sx, sy, 1.3, solid(0xFFFFF4D6)))

# monochrome (themed icon): ring + iris silhouette
mono = [dict(path=circle_path(C, C, 30.6) + circle_path(C, C, 26.4)[0:0], pts=None, fill=solid(0xFFFFFFFF))]
mono_paths = []
ring = (f"M{f(C - 30.6)},{f(C)}a30.6,30.6 0 1,0 61.2,0a30.6,30.6 0 1,0 -61.2,0z"
        f"M{f(C - 26.6)},{f(C)}a26.6,26.6 0 1,1 53.2,0a26.6,26.6 0 1,1 -53.2,0z")
mono_paths.append(ring)
for k in range(6):
    th = 60 * k - 90
    outer = arc_pts(RO - 1.6, th - 18, th + 34, 12)
    h1 = polar(RI + 0.6, th + 90 - 26)
    h0 = polar(RI + 0.6, th + 30 - 18)
    mono_paths.append(poly_path(outer + [h1, h0]))


# ---------------------------------------------------------------- VectorDrawable XML

def argb(c):
    return f"#{c:08X}"


def vd_shape(s):
    fill = s["fill"]
    stroke = s.get("stroke")
    attrs = f'android:pathData="{s["path"]}"'
    if stroke:
        attrs += f' android:strokeColor="{argb(stroke[0])}" android:strokeWidth="{stroke[1]}" android:strokeLineJoin="round"'
    if fill[0] == "solid":
        return f'    <path {attrs} android:fillColor="{argb(fill[1])}" />\n'
    if fill[0] == "linear":
        _, x0, y0, x1, y1, stops = fill
        g = f'android:type="linear" android:startX="{f(x0)}" android:startY="{f(y0)}" android:endX="{f(x1)}" android:endY="{f(y1)}"'
    else:
        _, cx, cy, r, stops = fill
        g = f'android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" android:gradientRadius="{f(r)}"'
    items = "".join(f'                <item android:offset="{o}" android:color="{argb(c)}" />\n' for o, c in stops)
    return (f'    <path {attrs}>\n        <aapt:attr name="android:fillColor">\n            <gradient {g}>\n'
            f'{items}            </gradient>\n        </aapt:attr>\n    </path>\n')


def vector(shapes):
    body = "".join(vd_shape(s) for s in shapes)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '</vector>\n')


def mono_vector():
    body = "".join(f'    <path android:pathData="{p}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd" />\n' for p in mono_paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '</vector>\n')


# ---------------------------------------------------------------- preview raster

S = 8  # supersampling: 108 * 8 = 864px canvas
N = 108 * S
yy, xx = np.mgrid[0:N, 0:N].astype(np.float32) / S


def unpack(c):
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255], np.float32) / 255.0


def fill_image(fill):
    if fill[0] == "solid":
        img = np.empty((N, N, 4), np.float32); img[:] = unpack(fill[1]); return img
    if fill[0] == "linear":
        _, x0, y0, x1, y1, stops = fill
        dx, dy = x1 - x0, y1 - y0
        t = ((xx - x0) * dx + (yy - y0) * dy) / (dx * dx + dy * dy)
    else:
        _, cx, cy, r, stops = fill
        t = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    t = np.clip(t, 0, 1)
    offs = np.array([o for o, _ in stops], np.float32)
    cols = np.stack([unpack(c) for _, c in stops])
    img = np.empty((N, N, 4), np.float32)
    for ch in range(4):
        img[..., ch] = np.interp(t, offs, cols[:, ch])
    return img


def composite(canvas, layer, mask):
    a = layer[..., 3] * mask
    canvas[..., :3] = layer[..., :3] * a[..., None] + canvas[..., :3] * (1 - a[..., None])
    canvas[..., 3] = a + canvas[..., 3] * (1 - a)


def render(shapes, canvas):
    for s in shapes:
        if s["pts"] is None:
            continue
        m = Image.new("L", (N, N), 0)
        ImageDraw.Draw(m).polygon([(x * S, y * S) for x, y in s["pts"]], fill=255)
        composite(canvas, fill_image(s["fill"]), np.asarray(m, np.float32) / 255.0)
        if s.get("stroke"):
            c, w = s["stroke"]
            m2 = Image.new("L", (N, N), 0)
            ImageDraw.Draw(m2).line([(x * S, y * S) for x, y in s["pts"] + [s["pts"][0]]], fill=255, width=max(1, int(w * S)))
            composite(canvas, fill_image(solid(c)), np.asarray(m2, np.float32) / 255.0)


def preview(path):
    canvas = np.zeros((N, N, 4), np.float32)
    render(background, canvas)
    render(fg, canvas)
    img = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), "RGBA")
    # Launcher masks: circle, squircle-ish rounded square, and full-bleed, at a few sizes.
    out = Image.new("RGBA", (N * 3 + 80, N + 40), (240, 240, 240, 255))
    for i, shape in enumerate(["circle", "rounded", "square"]):
        mask = Image.new("L", (N, N), 0)
        d = ImageDraw.Draw(mask)
        inset = 9 * S  # launchers show the inner 90dp of 108
        if shape == "circle":
            d.ellipse([inset, inset, N - inset, N - inset], fill=255)
        elif shape == "rounded":
            d.rounded_rectangle([inset, inset, N - inset, N - inset], radius=26 * S, fill=255)
        else:
            d.rectangle([inset, inset, N - inset, N - inset], fill=255)
        tile = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        tile.paste(img, (0, 0), mask)
        out.paste(tile, (20 + i * (N + 20), 20), tile)
    out.convert("RGB").save(path)


if __name__ == "__main__":
    res = sys.argv[1]
    os.makedirs(os.path.join(res, "drawable"), exist_ok=True)
    open(os.path.join(res, "drawable", "ic_launcher_background.xml"), "w").write(vector(background))
    open(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), "w").write(vector(fg))
    open(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), "w").write(mono_vector())
    preview(sys.argv[2])
    print("written")
