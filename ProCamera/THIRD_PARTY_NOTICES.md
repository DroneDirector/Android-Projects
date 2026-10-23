# VelaCut Pro — Third-party notices

VelaCut Pro includes the open-source software and AI models listed below. Their licences are
also shown in the app: **Settings (gear) → © Open-source licences**.

| Component | Copyright / author | Licence |
|---|---|---|
| AndroidX — CameraX, Media3 (ExoPlayer, Transformer, Effect), Jetpack Compose, Material 3, Material Icons, Activity, Lifecycle, Core, Security Crypto, ExifInterface, HeifWriter | The Android Open Source Project / Google LLC | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| LiteRT (TensorFlow Lite runtime) | Google LLC | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Kotlin standard library, kotlinx.coroutines | JetBrains s.r.o. and contributors | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Gson](https://github.com/google/gson) | Google LLC | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [RootEncoder](https://github.com/pedroSG94/RootEncoder) (live streaming) | pedroSG94 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| ESRGAN model (AI upscale) | TensorFlow authors (esrgan-tf2) | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| DeepLab v3 model (AI subject selection) | The TensorFlow Authors | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Google ML Kit — face detection, selfie segmentation, subject segmentation, image labelling (via Google Play services) | Google LLC | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) |
| Google Play services Wearable (watch remote connection) | Google LLC | [Android Software Development Kit License](https://developer.android.com/studio/terms) |
| Jetpack Compose for Wear OS (watch remote screen) | The Android Open Source Project | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [NAFNet](https://github.com/megvii-research/NAFNet) SIDD width-32 weights (AI noise reduction), [LiteRT conversion](https://huggingface.co/litert-community/NAFNet-SIDD-width32-LiteRT) | megvii-research | [MIT](https://github.com/megvii-research/NAFNet/blob/main/LICENSE) |
| [jxl-coder](https://github.com/awxkee/jxl-coder) (JPEG XL export) | Radzivon Bartoshyk | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) / BSD-3-Clause |
| [libjxl](https://github.com/libjxl/libjxl) (inside jxl-coder) | The JPEG XL Project Authors | BSD-3-Clause |
| [Brotli](https://github.com/google/brotli) (inside jxl-coder) | Google LLC | MIT |
| [LibRaw](https://github.com/LibRaw/LibRaw/tree/0.22.2) 0.22.2 (other cameras' RAW files) — unmodified, shipped as its own replaceable `libraw.so`, used only through its public C API. Includes code from dcraw (Dave Coffin), DCB / FBDD (Jacek Gozdz, BSD-3-Clause), X3F tools (Roland Karlsson, BSD) and Adobe DNG SDK (MIT). | LibRaw LLC | [LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html) (chosen from LibRaw's LGPL 2.1 / CDDL 1.0 dual licence) |
| [Natural Earth](https://www.naturalearthdata.com/) 1:110m land outlines (Library map) | Natural Earth | Public domain |
| [SIDD dataset](https://www.eecs.yorku.ca/~kamel/sidd/) (training data of the noise-reduction model) | Abdelhamed, Lin, Brown | MIT |

The full Apache 2.0, NAFNet MIT and LibRaw LGPL 2.1 licence texts are bundled with every build of the app.

**LibRaw source code.** LibRaw is used unmodified. Its exact source code (version 0.22.2) is at https://github.com/LibRaw/LibRaw/tree/0.22.2, and a copy is kept with this project's build files (`libraw/LibRaw-0.22.2-source.tar.gz` on the `models` branch) together with the build script (`ProCamera/native/veyroraw/build.sh`). Anyone may replace `libraw.so` inside the app with their own build of LibRaw; on request we provide the source code for at least three years.

## Trademarks

Names such as DJI D-Log / D-Log M, Sony S-Log3, Panasonic V-Log, ARRI LogC3, HLG, H.264 / H.265,
Topaz Labs, fal.ai, the camera / phone / drone makers named in the RAW looks (Google Pixel, Samsung Galaxy, Apple iPhone, DJI, Parrot, Autel, Canon, Sony, Nikon, Fujifilm, Leica, Ricoh, Panasonic, Olympus / OM System, YI), Whisper, BiRefNet, EVF-SAM, Bria, ElevenLabs and RIFE belong to their respective owners and
are used only to describe compatibility or the optional cloud models you can use with your own
fal.ai account. VelaCut Pro is not affiliated with or endorsed by them.
