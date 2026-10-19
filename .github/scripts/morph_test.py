"""Morph cut check on real video: a jump cut is made by skipping a stretch of a clip; the morph's
in-between frames are compared with the real frames that were skipped (and with a cross-dissolve)."""
import ctypes, sys, numpy as np, cv2, os
lib = ctypes.CDLL('./libmorph.so')
def rgba(x): return np.ascontiguousarray(cv2.cvtColor(x, cv2.COLOR_BGR2RGBA))
def morph(a, b, t):
    h, w = a.shape[:2]; out = np.zeros((h, w, 4), np.uint8)
    A, B = rgba(a), rgba(b)
    lib.morph_rgba(A.ctypes.data_as(ctypes.c_void_p), B.ctypes.data_as(ctypes.c_void_p), w, h, ctypes.c_float(t), out.ctypes.data_as(ctypes.c_void_p))
    return cv2.cvtColor(out, cv2.COLOR_RGBA2BGR)
def psnr(x, y): m = ((x.astype(float) - y.astype(float)) ** 2).mean(); return 10 * np.log10(255 ** 2 / max(m, 1e-9))
os.makedirs('out', exist_ok=True)
report = []
for path, starts, gap in [(p.split(':')[0], [int(s) for s in p.split(':')[1].split(',')], int(p.split(':')[2])) for p in sys.argv[1:]]:
    cap = cv2.VideoCapture(path)
    frames = []
    while True:
        ok, f = cap.read()
        if not ok: break
        frames.append(f)
    name = os.path.basename(path)
    report.append(f"{name}: {len(frames)} frames {frames[0].shape if frames else ''}")
    for s in starts:
        if s + gap >= len(frames): continue
        A, B = frames[s], frames[s + gap]
        h, w = A.shape[:2]
        k = min(1.0, 768 / max(w, h))
        A = cv2.resize(A, (int(w * k) // 2 * 2, int(h * k) // 2 * 2)); B = cv2.resize(B, (A.shape[1], A.shape[0]))
        row_m, row_t, row_x = [], [], []
        pm, px = [], []
        for j in range(1, gap):
            t = j / gap
            T = cv2.resize(frames[s + j], (A.shape[1], A.shape[0]))
            M = morph(A, B, t)
            X = (A.astype(float) * (1 - t) + B.astype(float) * t).astype(np.uint8)
            pm.append(psnr(M, T)); px.append(psnr(X, T))
            if j in (gap // 4, gap // 2, 3 * gap // 4):
                row_m.append(M); row_t.append(T); row_x.append(X)
        report.append(f"  cut {s}->{s+gap}: morph PSNR {np.mean(pm):.2f} dB (min {np.min(pm):.2f}) | cross-dissolve {np.mean(px):.2f} dB")
        sheet = np.vstack([np.hstack([A] + row_m + [B]), np.hstack([A] + row_t + [B]), np.hstack([A] + row_x + [B])])
        cv2.imwrite(f"out/{name}_{s}.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 88])
open('out/report.txt', 'w').write("\n".join(report) + "\nRows: morph / real skipped frames / cross-dissolve\n")
print("\n".join(report))
