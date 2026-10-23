"""Builds the website's three feature lists (Capture, Photo editor, Video editor) from ProCamera/FEATURES.md.
Usage: python3 features.py ProCamera/FEATURES.md Website/alcorcut-site/data"""
import html, json, re, sys

src, out = sys.argv[1], sys.argv[2]
text = open(src, encoding="utf-8").read()
want = {"Capture": "capture", "Photo editor": "photo", "Video editor": "video"}

def md(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"`(.+?)`", r"<code>\1</code>", s)
    return re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)

# The photo editor's list has no sub-headings: sort it into readable groups (first match wins).
PHOTO = [("RAW development", r"RAW|DNG|dcp|Upright"),
         ("Light, colour & tone", r"^(Light|Point curves|Parametric|Color:|HSL|Colour grading|My LUTs|Detail|Export a look)"),
         ("Masks & AI", r"mask|AI|Sky|Background|Generative"),
         ("Retouch & repair", r"Heal|Retouch|Lens corrections|Perspective|Crop"),
         ("Merges", r"Merge"),
         ("Library & workflow", r"Library|filmstrip|History|presets|Copy|LOAD|Batch|Open from|Skip|File-type|Panels|Resizable|histogram|Before|Real-time|Text &|Watermark|Fold-out|Quick tour"),
         ("Export & print", r"Export|WebP|IPTC")]

counts = {}
for part in re.split(r"^## ", text, flags=re.M)[1:]:
    title, _, body = part.partition("\n")
    key = want.get(title.strip())
    if not key:
        continue
    groups, cur = [], None
    for line in body.split("\n"):
        if line.startswith("### "):
            cur = {"title": line[4:].strip(), "items": []}; groups.append(cur)
        elif re.match(r"\s*- ", line):
            item = re.sub(r"^\s*- ", "", line).strip()
            if item.endswith(":") and len(item) < 40:
                continue   # a heading like "Pro develop:"
            if cur is None:
                cur = {"title": "Overview", "items": []}; groups.append(cur)
            cur["items"].append(md(item))
    groups = [g for g in groups if g["items"]]
    if key == "photo":
        items = [i for g in groups for i in g["items"]]
        sorted_groups = {k: [] for k, _ in PHOTO}
        for it in items:
            plain = re.sub("<[^>]+>", "", it)
            for k, rx in PHOTO:
                if re.search(rx, plain[:60]):
                    sorted_groups[k].append(it); break
            else:
                sorted_groups["Library & workflow"].append(it)
        groups = [{"title": k, "items": v} for k, v in sorted_groups.items() if v]
    json.dump(groups, open(f"{out}/features-{key}.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    counts[key] = sum(len(g["items"]) for g in groups)
print("features:", counts)
