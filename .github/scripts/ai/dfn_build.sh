set -ex
git clone -q --depth 1 --branch v0.5.6 https://github.com/Rikorose/DeepFilterNet.git dfn
cp dfn/libDF/Cargo.toml out/libDF_Cargo.toml; cp dfn/libDF/src/capi.rs out/capi.rs || true; ls dfn/models > out/models_list.txt
ls -la dfn/models
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install -q cargo-ndk
export ANDROID_NDK_HOME=$ANDROID_NDK_LATEST_HOME
cd dfn/libDF
# Host build + test of the C API first.
cargo build -q --release --features capi,default-model --lib 2>&1 | tail -5
ls -la ../target/release | grep -i "\.so\|\.a" | tee ../../out/host_libs.txt
cd ../..
# Android builds.
cd dfn/libDF
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -P 24 -o ../../out/jniLibs build --release --features capi,default-model --lib 2>&1 | tail -20
cd ../..
find out/jniLibs -type f | xargs ls -la
# Quick host check: denoise a noisy speech clip with the C API.
pip install -q numpy soundfile
curl -fsSL -o speech.wav https://raw.githubusercontent.com/Rikorose/DeepFilterNet/main/assets/noisy_snr0.wav || true
ls -la speech.wav || true
cat > t.py <<'PY'
import ctypes, numpy as np, soundfile as sf, glob, sys
lib = ctypes.CDLL(glob.glob('dfn/target/release/libdeep_filter*.so')[0])
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
