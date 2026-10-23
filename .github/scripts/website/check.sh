#!/usr/bin/env bash
# Checks the AlcorCut pages on the test WordPress and takes screenshots. Run from the repo root.
set -u
O="$PWD/out"; mkdir -p "$O"; S="$HOME/site"; fail=0
wp --path="$S" option get acs_pages --format=json > /tmp/pages.json; echo "acs_pages: $(cat /tmp/pages.json)" | tee "$O/pages.txt"
wp --path="$S" post list --post_type=page --fields=ID,post_title,post_name,page_template >> "$O/pages.txt"
python3 - > /tmp/urls.json <<'PY'
import json,subprocess
ids=json.load(open('/tmp/pages.json'))
out={k:subprocess.check_output(['wp','--path='+__import__('os').environ['HOME']+'/site','post','url',str(v)]).decode().strip() for k,v in ids.items()}
out['home_id']=ids['home']; print(json.dumps(out))
PY
echo "urls: $(cat /tmp/urls.json)" | tee -a "$O/pages.txt"
for k in home capture photo video; do
  url=$(python3 -c "import json;print(json.load(open('/tmp/urls.json'))['$k'])")
  code=$(curl -s -o "/tmp/$k.html" -w '%{http_code}' "$url"); n=$(grep -o 'class="acs acs-' "/tmp/$k.html" | wc -l)
  echo "$k $url -> HTTP $code, $n AlcorCut sections" | tee -a "$O/pages.txt"
  [ "$code" = 200 ] && [ "$n" -ge 4 ] || fail=1
done
cp /tmp/home.html "$O/home.html"
curl -s http://127.0.0.1:8080/ | grep -q 'acs-hero' && echo "front page = AlcorCut home" | tee -a "$O/pages.txt" || fail=1
cp "$S/wp-content/debug.log" "$O/debug-all.log" 2>/dev/null; grep -i "plugins/alcorcut-site" "$O/debug-all.log" > "$O/plugin-errors.log" && fail=1 || echo "none" > "$O/plugin-errors.log"
rm -f "$O/debug-all.log"
python3 .github/scripts/website/shots.py http://127.0.0.1:8080 "$(cat /tmp/urls.json)" || fail=1
exit $fail
