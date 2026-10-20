set -ex
git clone -q --depth 1 --branch v0.5.6 https://github.com/Rikorose/DeepFilterNet.git dfn
cp dfn/libDF/Cargo.toml out/libDF_Cargo.toml; cp dfn/libDF/src/capi.rs out/capi.rs || true; ls dfn/models > out/models_list.txt
ls -la dfn/models
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install -q cargo-ndk
export ANDROID_NDK_HOME=$ANDROID_NDK_LATEST_HOME
cd dfn
# Old 'time' versions don't build with today's Rust.
cargo update -p time 2>&1 | tail -3
cd libDF
# Host build + test of the C API first (a C library: cdylib).
cargo rustc --lib --release --no-default-features --features capi --crate-type cdylib 2>&1 | tail -5
ls -la ../target/release | grep -i "\.so" | tee ../../out/host_libs.txt
# Android builds.
for abi in arm64-v8a armeabi-v7a x86_64; do
  cargo ndk -t $abi -P 24 rustc --lib --release --no-default-features --features capi --crate-type cdylib 2>&1 | tail -3
done
cd ../..
mkdir -p out/jniLibs
for p in aarch64-linux-android:arm64-v8a armv7-linux-androideabi:armeabi-v7a x86_64-linux-android:x86_64; do
  t=${p%%:*}; a=${p##*:}; mkdir -p out/jniLibs/$a; cp dfn/target/$t/release/libdf.so out/jniLibs/$a/libdf.so && $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip out/jniLibs/$a/libdf.so
done
find out/jniLibs -type f | xargs ls -la
# Quick host check: denoise a noisy speech clip with the C API.
pip install -q numpy soundfile
curl -fsSL -o speech.wav https://raw.githubusercontent.com/Rikorose/DeepFilterNet/main/assets/noisy_snr0.wav || true
ls -la speech.wav || true
cat > t.py <<'PY'
import ctypes, numpy as np, soundfile as sf, glob, sys
lib = ctypes.CDLL(glob.glob('dfn/target/release/libdf.so')[0])
lib.df_create.restype = ctypes.c_void_p
lib.df_create.argtypes = [ctypes.c_char_p, ctypes.c_float, ctypes.c_char_p]
lib.df_get_frame_length.argtypes = [ctypes.c_void_p]; lib.df_get_frame_length.restype = ctypes.c_size_t
lib.df_process_frame.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.POINTER(ctypes.c_float)]; lib.df_process_frame.restype = ctypes.c_float
st = lib.df_create(None, 100.0, None)
n = lib.df_get_frame_length(st)
x, sr = sf.read('speech.wav', dtype='float32')
if x.ndim > 1: x = x.mean(1)
print('sr', sr, 'len', len(x), 'frame', n)
y = np.zeros_like(x)
import time; t=time.time()
for i in range(0, len(x) - n, n):
    a = np.ascontiguousarray(x[i:i+n]); b = np.zeros(n, np.float32)
    lib.df_process_frame(st, a.ctypes.data_as(ctypes.POINTER(ctypes.c_float)), b.ctypes.data_as(ctypes.POINTER(ctypes.c_float)))
    y[i:i+n] = b
print('rtf', (time.time()-t)/(len(x)/sr))
sf.write('out/denoised.wav', y, sr)
print('rms in', float(np.sqrt((x**2).mean())), 'out', float(np.sqrt((y**2).mean())))
PY
python3 t.py || true

# Keep the Android libraries on the 'models' branch for the app build.
git fetch -q --depth 1 origin models && git worktree add -q ../mb FETCH_HEAD
mkdir -p ../mb/dfn && cp -r out/jniLibs/* ../mb/dfn/ && cp dfn/LICENSE-MIT ../mb/dfn/DeepFilterNet-LICENSE-MIT && cp dfn/LICENSE-APACHE ../mb/dfn/DeepFilterNet-LICENSE-APACHE
cd ../mb && git add -A && git -c user.name=github-actions -c user.email=actions@github.com commit -qm "DeepFilterNet3 libraries (v0.5.6, capi)" && git push -q "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" HEAD:models
ls -la dfn/*
