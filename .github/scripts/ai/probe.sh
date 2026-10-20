set -x
api() { curl -fsSL "$1"; }
api https://api.github.com/repos/Rikorose/DeepFilterNet/releases | python3 -c "import json,sys; [print(r['tag_name'], [(a['name'],a['size'],a['browser_download_url']) for a in r['assets']]) for r in json.load(sys.stdin)[:6]]" > out/dfn_releases.txt
for m in onnx-community/depth-anything-v2-small onnx-community/Kokoro-82M-v1.0-ONNX Carve/LaMa-ONNX hexgrad/Kokoro-82M; do
  api "https://huggingface.co/api/models/$m/tree/main?recursive=true" | python3 -c "import json,sys; [print(f.get('path'), f.get('size')) for f in json.load(sys.stdin)]" > "out/hf_$(echo $m | tr / _).txt" || echo fail > "out/hf_$(echo $m | tr / _).txt"
  api "https://huggingface.co/api/models/$m" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('cardData',{}).get('license'), d.get('tags'))" >> "out/hf_$(echo $m | tr / _).txt" || true
done
api https://api.github.com/repos/hexgrad/misaki/contents/misaki/data | python3 -c "import json,sys; [print(f['name'], f['size']) for f in json.load(sys.stdin)]" > out/misaki_data.txt || true
api https://raw.githubusercontent.com/hexgrad/misaki/main/LICENSE | head -5 > out/misaki_license.txt || true
api https://raw.githubusercontent.com/hexgrad/misaki/main/misaki/en.py > out/misaki_en.py || true
api https://huggingface.co/hexgrad/Kokoro-82M/raw/main/config.json > out/kokoro_config.json || true
api https://api.github.com/repos/as-ideas/DeepPhonemizer/releases | python3 -c "import json,sys; [print(r['tag_name'], [(a['name'],a['browser_download_url']) for a in r['assets']]) for r in json.load(sys.stdin)[:5]]" > out/dp_releases.txt || true
api https://raw.githubusercontent.com/Rikorose/DeepFilterNet/main/libDF/src/tract.rs > out/dfn_tract.rs || true
api https://raw.githubusercontent.com/Rikorose/DeepFilterNet/main/libDF/src/lib.rs > out/dfn_lib.rs || true
api https://raw.githubusercontent.com/Rikorose/DeepFilterNet/main/LICENSE-MIT | head -3 > out/dfn_license.txt || true
ls -la out
