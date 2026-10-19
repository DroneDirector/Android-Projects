cmake_minimum_required(VERSION 3.22.1)
project(veyrocut_native C)

# RNNoise (BSD 3-clause): AI voice isolation.
file(GLOB RNNOISE_SRC ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/*.c)
add_library(veyrocut_native SHARED voice_jni.c ${RNNOISE_SRC})
target_include_directories(veyrocut_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/include ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src)
target_compile_options(veyrocut_native PRIVATE -O3 -Wno-unused -w)
target_link_libraries(veyrocut_native m)
