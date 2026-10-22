"""Daily check of raw.pixls.us for new RAW sample files (run by raw-samples-watch.yml).

- New model from a maker already in .github/raw-samples.txt -> proposed (one file per model).
- New maker -> proposed separately.
Nothing is added here: the owner is asked first (an issue); "RAW samples add" adds what was approved.
Everything already seen is remembered in .github/raw-samples-seen.txt.
"""
import os, sys

RAW_EXT = {"dng", "arw", "srf", "sr2", "cr2", "cr3", "crw", "nef", "nrw", "raf", "orf", "rw2", "rwl", "pef",
           "srw", "3fr", "fff", "iiq", "mos", "erf", "kdc", "dcr", "mrw", "mef", "x3f", "rwz", "gpr"}

def maker(m):
    m = m.lower().strip()
    for a, b in [("nikon corporation", "nikon"), ("leica camera ag", "leica"), ("om digital solutions", "olympus"),
                 ("om system", "olympus"), ("olympus imaging corp.", "olympus"), ("autel robotics", "autel"),
                 ("blackmagic design", "blackmagic"), ("yi technology", "xiaoyi"), ("hmd global", "nokia"),
                 ("moto g8plus", "motorola"), ("samsung techwin", "samsung"), ("phase one as", "phase one")]:
        if m == a: m = b
    return m.split(" ")[0]

def main(filelist, samples, seen_path, out_added, out_new):
    paths = [l.split("*", 1)[1].strip() for l in open(filelist, encoding="utf-8", errors="replace") if "*" in l]
    paths = [p for p in paths if p.rsplit(".", 1)[-1].lower() in RAW_EXT]
    listed = [l.strip() for l in open(samples, encoding="utf-8") if l.strip() and not l.startswith("#")]
    known_makers = {maker(p.split("/")[0]) for p in listed}
    tested_models = {(maker(p.split("/")[0]), p.split("/")[1].lower()) for p in listed if p.count("/") >= 2}
    first_run = not os.path.exists(seen_path)
    seen = set() if first_run else {l.rstrip("\n") for l in open(seen_path, encoding="utf-8")}
    added, new_makers = [], {}
    if not first_run:
        for p in paths:
            if p in seen or p.count("/") < 2: continue
            mk = maker(p.split("/")[0]); model = p.split("/")[1]
            if mk not in known_makers:
                new_makers.setdefault(p.split("/")[0], set()).add(model)
            elif (mk, model.lower()) not in tested_models:
                added.append(p); tested_models.add((mk, model.lower()))
    with open(seen_path, "w", encoding="utf-8") as f: f.write("\n".join(sorted(set(paths) | seen)) + "\n")
    with open(out_added, "w", encoding="utf-8") as f: f.write("".join(p + "\n" for p in added))
    with open(out_new, "w", encoding="utf-8") as f:
        f.write("".join(f"{m}: {', '.join(sorted(v))}\n" for m, v in sorted(new_makers.items())))
    print(f"first run: {first_run}; new files to add: {len(added)}; new makers: {len(new_makers)}")

if __name__ == "__main__":
    main(*sys.argv[1:6])
