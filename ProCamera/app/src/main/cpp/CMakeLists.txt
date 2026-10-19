cmake_minimum_required(VERSION 3.22.1)
project(veyrocut_native C CXX)
set(CMAKE_CXX_STANDARD 17)

# RNNoise (BSD 3-clause): AI voice isolation.
file(GLOB RNNOISE_SRC ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/*.c)
add_library(veyrocut_native SHARED voice_jni.c ${RNNOISE_SRC})
target_include_directories(veyrocut_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/include ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src)
target_compile_options(veyrocut_native PRIVATE -O3 -Wno-unused -w)
target_link_libraries(veyrocut_native m)

# whisper.cpp (MIT): on-device speech-to-text for text-based editing (CPU backend only).
set(W ${CMAKE_CURRENT_SOURCE_DIR}/whisper)
add_library(veyrocut_whisper SHARED
    whisper_jni.cpp
    ${W}/src/whisper.cpp
    ${W}/ggml/src/ggml.c
    ${W}/ggml/src/ggml-alloc.c
    ${W}/ggml/src/ggml-backend.cpp
    ${W}/ggml/src/ggml-backend-reg.cpp
    ${W}/ggml/src/ggml-quants.c
    ${W}/ggml/src/ggml-threading.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu.c
    ${W}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu-hbm.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu-quants.c
    ${W}/ggml/src/ggml-cpu/ggml-cpu-traits.cpp
    ${W}/ggml/src/ggml-cpu/unary-ops.cpp
    ${W}/ggml/src/ggml-cpu/binary-ops.cpp
)
target_compile_definitions(veyrocut_whisper PRIVATE GGML_USE_CPU _GNU_SOURCE)
target_include_directories(veyrocut_whisper PRIVATE ${W} ${W}/src ${W}/include ${W}/ggml/include ${W}/ggml/src ${W}/ggml/src/ggml-cpu)
target_compile_options(veyrocut_whisper PRIVATE -O3 -w -fvisibility=hidden -ffunction-sections -fdata-sections)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(veyrocut_whisper PRIVATE -march=armv8.2-a+fp16)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(veyrocut_whisper PRIVATE -mfpu=neon-vfpv4)
endif ()
target_link_options(veyrocut_whisper PRIVATE -Wl,--gc-sections)
find_library(LOG_LIB log)
target_link_libraries(veyrocut_whisper ${LOG_LIB} android m)
