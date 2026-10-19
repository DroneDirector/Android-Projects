"""Reference of VeyroCut Pro's Magic Mask tracker (MagicTracker.kt follows the same steps):
crop-based MobileSAM fitting + optical-flow carrying + cut / loss handling."""
import ctypes, numpy as np, cv2, onnxruntime as ort, os
HERE = os.path.dirname(os.path.abspath(__file__))
lib = ctypes.CDLL(os.environ.get('MORPH_LIB', './libmorph.so'))
M = os.environ.get('SAM_DIR', 'models')
so = ort.SessionOptions(); so.intra_op_num_threads = os.cpu_count()
enc = ort.InferenceSession(f'{M}/sam_encoder.onnx', so); dec = ort.InferenceSession(f'{M}/sam_decoder.onnx', so)
MEAN = np.array([123.675, 116.28, 103.53], np.float32)
GRID = 256          # long side of the whole-frame working grid (flow, carried outline)
MAXMASK = 512       # long side cap of a stored crop mask
PAD = float(os.environ.get('PAD', '0.35'))  # box padding (share of the object's size): room for parts that come back into view

def encode(rgb):
    h, w = rgb.shape[:2]; k = 1024 / max(h, w)
    r = cv2.resize(rgb, (max(1, round(w * k)), max(1, round(h * k))), interpolation=cv2.INTER_AREA if k < 1 else cv2.INTER_CUBIC).astype(np.float32)
    c = np.empty((1024, 1024, 3), np.float32); c[:] = MEAN; c[:r.shape[0], :r.shape[1]] = r
    return dict(emb=enc.run(None, {'image': c.transpose(2, 0, 1)[None]})[0], k=k, w=w, h=h)

def decode(e, pts, hint=None):
    L = [[p[0] * e['w'] * e['k'], p[1] * e['h'] * e['k'], p[2]] for p in pts]
    if not any(p[2] in (2, 3) for p in pts): L.append([0, 0, -1])
    L = np.array(L, np.float32)
    m, s = dec.run(None, {'embeddings': e['emb'], 'coords': L[None, :, :2], 'labels': L[None, :, 2],
                          'mask_input': (hint if hint is not None else np.zeros((256, 256), np.float32))[None, None].astype(np.float32),
                          'has_mask': np.array([1 if hint is not None else 0], np.float32)})
    return m[0], s[0]

def logits_to(e, logits, ow, oh):
    """Logits square → probability map over the encoded picture at ow × oh."""
    sq = cv2.resize(logits, (1024, 1024), interpolation=cv2.INTER_LINEAR)[:max(1, round(e['h'] * e['k'])), :max(1, round(e['w'] * e['k']))]
    v = cv2.resize(sq, (ow, oh), interpolation=cv2.INTER_LINEAR)
    return 1 / (1 + np.exp(-v * 1.5))

def flow(a, b):
    h, w = a.shape[:2]; out = np.zeros((h, w, 2), np.float32)
    A = np.ascontiguousarray(cv2.cvtColor(a, cv2.COLOR_RGB2RGBA)); B = np.ascontiguousarray(cv2.cvtColor(b, cv2.COLOR_RGB2RGBA))
    lib.flow_only(A.ctypes.data_as(ctypes.c_void_p), B.ctypes.data_as(ctypes.c_void_p), w, h, out.ctypes.data_as(ctypes.c_void_p))
    return out

def gridsize(W, H):
    k = GRID / max(W, H); return max(16, round(W * k)), max(16, round(H * k))

def is_cut(a, b):
    ta = cv2.resize(a, (32, 18), interpolation=cv2.INTER_AREA).astype(np.float32); tb = cv2.resize(b, (32, 18), interpolation=cv2.INTER_AREA).astype(np.float32)
    d = np.abs(ta - tb).mean() / 255
    ha = cv2.calcHist([a], [0, 1, 2], None, [4, 4, 4], [0, 256] * 3).ravel(); hb = cv2.calcHist([b], [0, 1, 2], None, [4, 4, 4], [0, 256] * 3).ravel()
    hd = np.abs(ha / ha.sum() - hb / hb.sum()).sum() / 2
    return d > 0.16 and hd > 0.25

def rect_for(gmask, W, H):
    """Crop (normalised x0, y0, x1, y1) around the outline: twice its size, at least 1/5 of the frame, square in pixels."""
    gh, gw = gmask.shape
    ys, xs = np.nonzero(gmask > 0.5)
    if len(xs) < 3: return None
    x0, x1 = (xs.min() / gw) * W, ((xs.max() + 1) / gw) * W; y0, y1 = (ys.min() / gh) * H, ((ys.max() + 1) / gh) * H
    side = max(x1 - x0, y1 - y0) * 2.0 + 16
    side = max(side, 0.2 * max(W, H))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    sw, sh = min(side, W), min(side, H)
    rx0 = min(max(cx - sw / 2, 0), W - sw); ry0 = min(max(cy - sh / 2, 0), H - sh)
    return (rx0 / W, ry0 / H, (rx0 + sw) / W, (ry0 + sh) / H)

def crop(img, r):
    H, W = img.shape[:2]
    x0, y0, x1, y1 = round(r[0] * W), round(r[1] * H), round(r[2] * W), round(r[3] * H)
    return img[y0:max(y0 + 1, y1), x0:max(x0 + 1, x1)]

def entry_to_grid(ent, gw, gh):
    """A crop mask → probability over the whole-frame grid."""
    g = np.zeros((gh, gw), np.float32)
    if ent is None or ent['rect'] is None: return g
    r = ent['rect']; m = ent['mask'].astype(np.float32) / 255
    x0, y0, x1, y1 = r[0] * gw, r[1] * gh, r[2] * gw, r[3] * gh
    gx, gy = np.meshgrid(np.arange(gw) + 0.5, np.arange(gh) + 0.5)
    u = (gx - x0) / max(1e-6, x1 - x0) * m.shape[1] - 0.5; v = (gy - y0) / max(1e-6, y1 - y0) * m.shape[0] - 0.5
    inside = (gx >= x0) & (gx <= x1) & (gy >= y0) & (gy <= y1)
    s = cv2.remap(m, u.astype(np.float32), v.astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    return np.where(inside, s, 0)

def clean(mask):
    """Drops stray specks: keeps the largest piece and any piece at least 8 % of its size."""
    b = (mask > 127).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(b, connectivity=8)
    if n <= 2: return mask
    areas = stats[1:, cv2.CC_STAT_AREA]; big = areas.max()
    keep = np.isin(lab, [i + 1 for i, a in enumerate(areas) if a >= 0.08 * big]).astype(np.uint8)
    keep = cv2.dilate(keep, np.ones((5, 5), np.uint8))
    return (mask.astype(np.float32) * keep).astype(np.uint8)

def fit(frame, rect, pts_crop, hint):
    """SAM on the crop: returns (logits square, crop mask uint8, encoder geometry)."""
    c = crop(frame, rect)
    e = encode(c)
    m4, s4 = decode(e, pts_crop, hint)
    logits = m4[0]
    ch, cw = c.shape[:2]; k = min(1, MAXMASK / max(cw, ch)); mw, mh = max(8, round(cw * k)), max(8, round(ch * k))
    prob = logits_to(e, logits, mw, mh)
    return logits, clean((prob * 255).astype(np.uint8)), e

def prompts_from(gmask, rect, W, H):
    """Box round the carried outline and the point deepest inside it, in crop coordinates."""
    gh, gw = gmask.shape
    b = (gmask > 0.5).astype(np.uint8)
    ys, xs = np.nonzero(b)
    x0, x1, y0, y1 = xs.min() / gw, (xs.max() + 1) / gw, ys.min() / gh, (ys.max() + 1) / gh
    px, py = (x1 - x0) * PAD + 1 / gw, (y1 - y0) * PAD + 1 / gh
    d = cv2.distanceTransform(b, cv2.DIST_L2, 3); yy, xx = np.unravel_index(np.argmax(d), d.shape)
    def tc(x, y): return ((x - rect[0]) / (rect[2] - rect[0]), (y - rect[1]) / (rect[3] - rect[1]))
    p = tc((xx + 0.5) / gw, (yy + 0.5) / gh); a = tc(x0 - px, y0 - py); z = tc(x1 + px, y1 + py)
    cl = lambda v: min(1, max(0, v))
    return [(cl(p[0]), cl(p[1]), 1), (cl(a[0]), cl(a[1]), 2), (cl(z[0]), cl(z[1]), 3)]

def hint_for(prev, rect, W, H, back, bad=None):
    """The previous answer carried into the new crop's model square (logits; −20 outside)."""
    cw, ch = (rect[2] - rect[0]) * W, (rect[3] - rect[1]) * H
    k = 1024 / max(cw, ch)
    cx, cy = np.meshgrid((np.arange(256) + 0.5) * 4, (np.arange(256) + 0.5) * 4)
    inn = (cx <= cw * k) & (cy <= ch * k)
    nx = rect[0] + (cx / k) / W; ny = rect[1] + (cy / k) / H          # whole-frame normalised
    bx, by = back(nx, ny)
    if prev is None or prev['rect'] is None: return None
    pr = prev['rect']; pe = prev['geom']
    pcw, pch = (pr[2] - pr[0]) * W, (pr[3] - pr[1]) * H
    pk = 1024 / max(pcw, pch)
    sx = ((bx - pr[0]) * W) * pk / 4 - 0.5; sy = ((by - pr[1]) * H) * pk / 4 - 0.5
    h = cv2.remap(prev['logits'], sx.astype(np.float32), sy.astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=-20)
    h[~inn] = -20
    if bad is not None:
        gh, gw = bad.shape
        bi = bad[np.clip((ny * gh).astype(int), 0, gh - 1), np.clip((nx * gw).astype(int), 0, gw - 1)]
        h[bi] = np.minimum(h[bi], -2.0)
    return h

def start(frame, pts):
    """The picked frame: the whole picture first, then a close-up of the object for a finer outline."""
    H, W = frame.shape[:2]; gw, gh = gridsize(W, H)
    e = encode(frame)
    m4, s4 = decode(e, pts)
    pick = 0 if (len(pts) > 1 or pts[0][2] != 1) else 1 + int(np.argmax(s4[1:]))
    full = m4[pick]
    g = logits_to(e, full, gw, gh)
    rect = rect_for(g, W, H)
    ent0 = dict(rect=(0, 0, 1, 1), logits=full, mask=(logits_to(e, full, min(MAXMASK, W), max(8, round(min(MAXMASK, W) * H / W))) * 255).astype(np.uint8), geom=e)
    if rect is None or rect == (0, 0, 1, 1): return ent0
    pc = prompts_from(g, rect, W, H)
    # Keep the user's own points too (in crop coordinates).
    for p in pts:
        if p[2] in (0, 1): pc.append(((p[0] - rect[0]) / (rect[2] - rect[0]), (p[1] - rect[1]) / (rect[3] - rect[1]), p[2]))
    hint = hint_for(ent0, rect, W, H, lambda x, y: (x, y))
    logits, mask, ge = fit(frame, rect, pc, hint)
    return dict(rect=rect, logits=logits, mask=mask, geom=ge)

def advance(prev, prev_frame, frame):
    H, W = frame.shape[:2]; gw, gh = gridsize(W, H)
    if prev is None or prev['rect'] is None: return None
    a = cv2.resize(frame, (gw, gh), interpolation=cv2.INTER_AREA); b = cv2.resize(prev_frame, (gw, gh), interpolation=cv2.INTER_AREA)
    if is_cut(a, b): return None
    F = flow(a, b)  # current → previous
    def back(x, y):
        xi = np.clip(np.round(x * gw - 0.5).astype(int), 0, gw - 1); yi = np.clip(np.round(y * gh - 0.5).astype(int), 0, gh - 1)
        return x + F[yi, xi, 0] / gw, y + F[yi, xi, 1] / gh
    pg = entry_to_grid(prev, gw, gh)
    gx, gy = np.meshgrid((np.arange(gw) + 0.5) / gw, (np.arange(gh) + 0.5) / gh)
    bx, by = back(gx, gy)
    carried = cv2.remap(pg, (bx * gw - 0.5).astype(np.float32), (by * gh - 0.5).astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    # Newly uncovered pixels (no match in the previous frame) can't carry the outline.
    F2 = flow(b, a)  # previous → current
    px_, py_ = bx * gw - 0.5, by * gh - 0.5
    rx = cv2.remap(F2[..., 0], px_.astype(np.float32), py_.astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    ry = cv2.remap(F2[..., 1], px_.astype(np.float32), py_.astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    err = np.sqrt((F[..., 0] + rx) ** 2 + (F[..., 1] + ry) ** 2)
    bad = err > 1.5 + 0.05 * np.sqrt(F[..., 0] ** 2 + F[..., 1] ** 2)
    carried[bad] = 0
    # A cut the colours didn't give away: motion that doesn't agree both ways over most of the frame.
    if bad.mean() > 0.45: return None
    # The object suddenly mostly gone (left the picture, or the shot changed).
    pa = (pg > 0.5).sum()
    if pa >= 50 and (carried > 0.5).sum() < 0.25 * pa: return None
    if (carried > 0.5).sum() < 3: return None
    rect = rect_for(carried, W, H)
    pc = prompts_from(carried, rect, W, H)
    hint = hint_for(prev, rect, W, H, back, bad)
    logits, mask, ge = fit(frame, rect, pc, hint)
    ent = dict(rect=rect, logits=logits, mask=mask, geom=ge)
    fg = entry_to_grid(ent, gw, gh) > 0.5; cg = carried > 0.5
    if os.environ.get('VERBOSE'): print('  carried', cg.sum(), 'fitted', fg.sum())
    iou = (fg & cg).sum() / max(1, (fg | cg).sum())
    if iou < 0.35:
        # The AI lost it: keep the carried outline (as this crop's mask).
        r = rect; x0, y0 = r[0] * gw, r[1] * gh
        mw, mh = mask.shape[1], mask.shape[0]
        u = x0 + (np.arange(mw) + 0.5) / mw * (r[2] - r[0]) * gw - 0.5; v = y0 + (np.arange(mh) + 0.5) / mh * (r[3] - r[1]) * gh - 0.5
        uu, vv = np.meshgrid(u, v)
        cm = cv2.remap(carried.astype(np.float32), uu.astype(np.float32), vv.astype(np.float32), cv2.INTER_LINEAR)
        ent = dict(rect=rect, logits=hint if hint is not None else logits, mask=(cm * 255).astype(np.uint8), geom=ge)
    return ent

def full_mask(ent, W, H):
    if ent is None or ent['rect'] is None: return np.zeros((H, W), np.float32)
    return entry_to_grid(ent, W, H)
