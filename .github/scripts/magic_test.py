"""Magic Mask check on real video with the reference tracker (magic_ref.py = MagicTracker.kt's steps)."""
import sys, os, time, numpy as np, cv2
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import magic_ref as R

def run(path, start, n, click, out):
    cap = cv2.VideoCapture(path); cap.set(cv2.CAP_PROP_POS_FRAMES, start); frames = []
    for _ in range(n):
        ok, f = cap.read()
        if not ok: break
        frames.append(cv2.cvtColor(f, cv2.COLOR_BGR2RGB))
    H, W = frames[0].shape[:2]
    t = time.time()
    ents = [R.start(frames[0], [(click[0] / W, click[1] / H, 1)])]
    for i in range(1, len(frames)): ents.append(R.advance(ents[-1], frames[i - 1], frames[i]))
    areas = [float((R.full_mask(e, W, H) > 0.5).mean()) for e in ents]
    print(f"{path}@{start}: {len(frames)} frames in {time.time()-t:.1f}s; area first {areas[0]:.3f} min {min(areas):.3f} max {max(areas):.3f} last {areas[-1]:.3f}; lost at {next((i for i,e in enumerate(ents) if e is None), None)}")
    tiles = []
    for i in np.linspace(0, len(frames) - 1, 6).astype(int):
        f = frames[i].copy(); m = R.full_mask(ents[i], W, H) > 0.5
        f[m] = (f[m] * 0.45 + np.array([255, 179, 0]) * 0.55).astype(np.uint8)
        cnts, _ = cv2.findContours(m.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE); cv2.drawContours(f, cnts, -1, (255, 255, 255), 2)
        cv2.putText(f, str(start + i), (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        tiles.append(cv2.resize(f, (480, round(480 * H / W))))
    cv2.imwrite(out, cv2.cvtColor(np.vstack([np.hstack(tiles[:3]), np.hstack(tiles[3:])]), cv2.COLOR_RGB2BGR), [cv2.IMWRITE_JPEG_QUALITY, 85])

run('megamind.avi', 40, 90, (290, 230), 'out/magic_megamind.jpg')
run('vtest.avi', 100, 90, (360, 224), 'out/magic_vtest.jpg')
run('megamind.avi', 120, 60, (500, 200), 'out/magic_megamind2.jpg')
