set -e
TAG=ai-models-v1
mkdir -p d && cd d
get() { curl -fsSL --retry 3 -o "$2" "$1"; }
get https://storage.googleapis.com/download.tensorflow.org/models/tflite/esrgan/ESRGAN.tflite esrgan.tflite
get https://huggingface.co/litert-community/NAFNet-SIDD-width32-LiteRT/resolve/main/nafnet_sidd_width32_fp16.tflite nafnet.tflite
get https://storage.googleapis.com/download.tensorflow.org/models/tflite/gpu/deeplabv3_257_mv_gpu.tflite deeplab.tflite
get https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin whisper_ggml-base-q5_1.bin
get https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin whisper_ggml-small-q5_1.bin
git -C .. fetch -q --depth 1 origin models
for f in sam_encoder.onnx sam_decoder.onnx; do git -C .. show FETCH_HEAD:$f > $f; done
git -C .. show FETCH_HEAD:dfn/DeepFilterNet3_onnx.tar.gz > DeepFilterNet3_onnx.tar.gz
for f in ../ProCamera/app/src/main/assets/sound_library/soundtracks/*.m4a; do cp "$f" "sound_soundtracks_$(basename "$f")"; done
ls -la
gh release upload $TAG * -R "$GITHUB_REPOSITORY" --clobber
gh release view $TAG -R "$GITHUB_REPOSITORY" --json assets --jq '.assets[] | "\(.name) \(.size)"' | sort | tee ../out/assets.txt
