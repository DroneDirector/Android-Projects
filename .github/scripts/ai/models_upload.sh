set -ex
TAG=ai-models-v1
H=https://huggingface.co
mkdir -p m && cd m
get() { curl -fsSL --retry 3 -o "$2" "$1"; ls -la "$2"; }
get $H/onnx-community/depth-anything-v2-small/resolve/main/onnx/model.onnx depth_anything_v2_small.onnx
get $H/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx lama_fp32.onnx
get $H/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_quantized.onnx kokoro_v1_q8.onnx
get $H/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/tokenizer.json kokoro_tokenizer.json
cp kokoro_tokenizer.json ../out/
for v in af_heart af_bella af_nicole af_sarah am_michael am_fenrir am_puck bf_emma bf_isabella bm_george bm_fable bm_lewis; do get $H/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/voices/$v.bin kokoro_$v.bin; done
for f in us_gold us_silver gb_gold gb_silver; do get https://raw.githubusercontent.com/hexgrad/misaki/main/misaki/data/$f.json misaki_$f.json; done
get https://raw.githubusercontent.com/hexgrad/misaki/main/LICENSE misaki_LICENSE.txt
get $H/hexgrad/Kokoro-82M/raw/main/README.md kokoro_README.md
cp kokoro_README.md ../out/
head -c 3000 misaki_us_gold.json > ../out/us_gold_head.json
sha256sum * > SHA256SUMS.txt; cp SHA256SUMS.txt ../out/
gh release view $TAG -R "$GITHUB_REPOSITORY" >/dev/null 2>&1 || gh release create $TAG -R "$GITHUB_REPOSITORY" --title "AI models (downloaded by the app on first use)" --notes "Model files the app downloads when a feature is first used. Not an app release." --prerelease --latest=false
gh release upload $TAG * -R "$GITHUB_REPOSITORY" --clobber
gh release view $TAG -R "$GITHUB_REPOSITORY" --json assets --jq '.assets[] | "\(.name) \(.size)"' | tee ../out/assets.txt
