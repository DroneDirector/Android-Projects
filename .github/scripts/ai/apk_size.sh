set -e
gh release download veyrocutpro-1.0.331 -R "$GITHUB_REPOSITORY" -p '*.apk' -D a
unzip -lv a/*.apk | awk 'NR>3 && NF>=8 {print $1, $3, $8}' | sort -n -r > out/entries.txt
head -60 out/entries.txt
echo; echo "== by folder (uncompressed / stored)"
python3 - <<'PY'
import collections
d=collections.Counter(); c=collections.Counter()
for l in open('out/entries.txt'):
    p=l.split()
    if len(p)<3: continue
    n=p[2]; key='/'.join(n.split('/')[:2]) if n.startswith(('assets','lib')) else n.split('/')[0]
    d[key]+=int(p[0]); c[key]+=int(p[1])
for k,v in d.most_common(40): print(f"{v/1e6:8.1f} MB raw {c[k]/1e6:8.1f} MB packed  {k}")
PY
ls assets_list 2>/dev/null; unzip -l a/*.apk 'assets/sound_library/*' | tail -40
