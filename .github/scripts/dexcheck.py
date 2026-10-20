"""Fails the build when an app method comes close to Android's 256-register verifier limit
(such a method makes the whole class fail to load on the phone: a crash on opening that screen)."""
import sys, zipfile, struct, glob

LIMIT = 250
PKG = "Lcom/dronedirector/"

def uleb(b, p):
    r = 0; s = 0
    while True:
        x = b[p]; p += 1; r |= (x & 0x7f) << s; s += 7
        if x < 0x80: return r, p

def check(b, name, worst, bad):
    sn, soff = struct.unpack_from('<II', b, 0x38)
    def s(i):
        so, = struct.unpack_from('<I', b, soff + 4 * i)
        _, p = uleb(b, so); e = b.index(0, p); return b[p:e].decode('utf-8', 'replace')
    tn, toff = struct.unpack_from('<II', b, 0x40)
    def t(i): return s(struct.unpack_from('<I', b, toff + 4 * i)[0])
    mn, moff = struct.unpack_from('<II', b, 0x58)
    cn, coff = struct.unpack_from('<II', b, 0x60)
    for i in range(cn):
        f = struct.unpack_from('<8I', b, coff + 32 * i)
        cls = t(f[0])
        if not cls.startswith(PKG) or f[6] == 0: continue
        p = f[6]
        sf, p = uleb(b, p); inf, p = uleb(b, p); dm, p = uleb(b, p); vm, p = uleb(b, p)
        for _ in range(sf + inf): _, p = uleb(b, p); _, p = uleb(b, p)
        for count in (dm, vm):
            idx = 0
            for _ in range(count):
                d, p = uleb(b, p); idx += d; _, p = uleb(b, p); co, p = uleb(b, p)
                if not co: continue
                regs, = struct.unpack_from('<H', b, co)
                c, pr, nm = struct.unpack_from('<HHI', b, moff + 8 * idx)
                label = f"{cls[1:-1].replace('/', '.')}.{s(nm)} ({regs} registers)"
                if regs > worst[0]: worst[0] = regs; worst[1] = label
                # Screens' main functions (not their inner lambdas, which are verified differently) must stay below the limit.
                if regs >= LIMIT and '$' not in s(nm): bad.append(label)

worst = [0, ""]; bad = []
for apk in sys.argv[1:] or glob.glob("*.apk"):
    z = zipfile.ZipFile(apk)
    for n in z.namelist():
        if n.endswith('.dex'): check(z.read(n), n, worst, bad)
    break
print(f"Largest app method: {worst[1]}")
if bad:
    for b in sorted(set(bad)): print(f"::error::Method too large for Android's verifier (limit 256 registers): {b}")
    sys.exit(1)
