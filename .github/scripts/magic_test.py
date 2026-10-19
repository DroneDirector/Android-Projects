"""Magic Mask check on real video: the app's tracking loop (same steps as MagicTracker.kt) with
MobileSAM (ONNX) and the app's optical flow; writes contact sheets of the tracked outline."""
import ctypes, sys, numpy as np, cv2, onnxruntime as ort, time
lib = ctypes.CDLL('./libmorph.so')
enc = ort.InferenceSession('models/sam_encoder.onnx'); dec = ort.InferenceSession('models/sam_decoder.onnx')
MEAN = np.array([123.675, 116.28, 103.53], np.float32)
def encode(rgb):
    h, w = rgb.shape[:2]; k = 1024 / max(h, w)
    r = cv2.resize(rgb, (round(w * k), round(h * k)), interpolation=cv2.INTER_AREA).astype(np.float32)
    c = np.empty((1024, 1024, 3), np.float32); c[:] = MEAN; c[:r.shape[0], :r.shape[1]] = r
    return enc.run(None, {'image': c.transpose(2, 0, 1)[None]})[0], k, w, h
def decode(e, pts, hint=None):
    emb, k, w, h = e
    L = [[p[0] * w * k, p[1] * h * k, p[2]] for p in pts]
    if not any(p[2] in (2, 3) for p in pts): L.append([0, 0, -1])
    L = np.array(L, np.float32)
    m, s = dec.run(None, {'embeddings': emb, 'coords': L[None, :, :2], 'labels': L[None, :, 2], 'mask_input': (hint if hint is not None else np.zeros((256, 256), np.float32))[None, None].astype(np.float32), 'has_mask': np.array([1 if hint is not None else 0], np.float32)})
    return m[0], s[0]
def to_mask(logits, e, ow, oh):
    _, k, w, h = e
    sq = cv2.resize(logits, (1024, 1024))[:round(h * k), :round(w * k)]
    v = cv2.resize(sq, (ow, oh))
    return (255 / (1 + np.exp(-v * 1.5))).astype(np.uint8)
def flow(a, b):
    h, w = a.shape[:2]; out = np.zeros((h, w, 2), np.float32)
    A = np.ascontiguousarray(cv2.cvtColor(a, cv2.COLOR_RGB2RGBA)); B = np.ascontiguousarray(cv2.cvtColor(b, cv2.COLOR_RGB2RGBA))
    lib.flow_only(A.ctypes.data_as(ctypes.c_void_p), B.ctypes.data_as(ctypes.c_void_p), w, h, out.ctypes.data_as(ctypes.c_void_p))
    return out
def small(img, side):
    h, w = img.shape[:2]; k = side / max(h, w); return cv2.resize(img, (max(16, round(w * k)), max(16, round(h * k))), interpolation=cv2.INTER_AREA)
def deepest(m):
    d = cv2.distanceTransform((m > 127).astype(np.uint8), cv2.DIST_L2, 3); y, x = np.unravel_index(np.argmax(d), d.shape); return x, y
def advance(prev, frame, mw, mh):
    p_logits, p_mask, p_small = prev
    sm = small(frame, 256); fh, fw = sm.shape[:2]
    ps = cv2.resize(p_small, (fw, fh))
    F = flow(sm, ps)  # current -> previous
    def back_map(xs, ys):  # normalised coords -> where they were
        xi = np.clip(np.round(xs * fw - 0.5).astype(int), 0, fw - 1); yi = np.clip(np.round(ys * fh - 0.5).astype(int), 0, fh - 1)
        return xs + F[yi, xi, 0] / fw, ys + F[yi, xi, 1] / fh
    gx, gy = np.meshgrid((np.arange(mw) + 0.5) / mw, (np.arange(mh) + 0.5) / mh)
    sx, sy = back_map(gx, gy)
    carried = cv2.remap(p_mask.astype(np.float32), (sx * mw - 0.5).astype(np.float32), (sy * mh - 0.5).astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE).astype(np.uint8)
    e = encode(frame); _, k, w, h = e
    sqW, sqH = w * k, h * k
    ux, uy = np.meshgrid((np.arange(256) + 0.5) * 4, (np.arange(256) + 0.5) * 4)
    bx, by = back_map(np.clip(ux / sqW, 0, 1), np.clip(uy / sqH, 0, 1))
    hint = cv2.remap(p_logits, (bx * sqW / 4 - 0.5).astype(np.float32), (by * sqH / 4 - 0.5).astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    hint[(ux > sqW) | (uy > sqH)] = -20
    ys_, xs_ = np.nonzero(carried > 127)
    if len(xs_) < 4: return (hint, carried, sm)
    x0, x1, y0, y1 = xs_.min(), xs_.max(), ys_.min(), ys_.max()
    px, py = (x1 - x0) * 0.04 + 2, (y1 - y0) * 0.04 + 2
    dx, dy = deepest(carried)
    pts = [((dx + 0.5) / mw, (dy + 0.5) / mh, 1), (max(0, (x0 - px) / mw), max(0, (y0 - py) / mh), 2), (min(1, (x1 + 1 + px) / mw), min(1, (y1 + 1 + py) / mh), 3)]
    m4, s4 = decode(e, pts, hint)
    logits = m4[0]; fitted = to_mask(logits, e, mw, mh)
    p_ = fitted > 127; c_ = carried > 127
    iou = (p_ & c_).sum() / max(1, (p_ | c_).sum())
    return (hint, carried, sm) if iou < 0.35 else (logits, fitted, sm)

def run(path, start, n, click, out):
    cap = cv2.VideoCapture(path); frames = []
    cap.set(cv2.CAP_PROP_POS_FRAMES, start)
    for _ in range(n):
        ok, f = cap.read()
        if not ok: break
        frames.append(cv2.cvtColor(f, cv2.COLOR_BGR2RGB))
    f0 = frames[0]; h, w = f0.shape[:2]
    e0 = encode(f0)
    pts = [(click[0] / w, click[1] / h, 1)]
    m4, s4 = decode(e0, pts)
    pick = 1 + int(np.argmax(s4[1:]))
    k = 384 / max(h, w); mw, mh = round(w * k), round(h * k)
    st = (m4[pick], to_mask(m4[pick], e0, mw, mh), small(f0, 256))
    masks = [st[1]]; t = time.time()
    for f in frames[1:]:
        st = advance(st, f, mw, mh); masks.append(st[1])
    print(f"{path} {len(frames)} frames tracked in {time.time()-t:.1f}s; mask area first {np.mean(masks[0]>127):.3f} last {np.mean(masks[-1]>127):.3f}")
    tiles = []
    for i in np.linspace(0, len(frames) - 1, 6).astype(int):
        f = frames[i].copy(); m = cv2.resize(masks[i], (w, h)) > 127
        f[m] = (f[m] * 0.45 + np.array([255, 179, 0]) * 0.55).astype(np.uint8)
        cnts, _ = cv2.findContours(m.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE); cv2.drawContours(f, cnts, -1, (255, 255, 255), 2)
        cv2.putText(f, str(start + i), (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        tiles.append(cv2.resize(f, (480, round(480 * h / w))))
    cv2.imwrite(out, cv2.cvtColor(np.vstack([np.hstack(tiles[:3]), np.hstack(tiles[3:])]), cv2.COLOR_RGB2BGR), [cv2.IMWRITE_JPEG_QUALITY, 85])

run('megamind.avi', 40, 90, (290, 230), 'out/magic_megamind.jpg')
run('vtest.avi', 100, 90, (360, 224), 'out/magic_vtest.jpg')
run('megamind.avi', 120, 60, (500, 200), 'out/magic_megamind2.jpg')
