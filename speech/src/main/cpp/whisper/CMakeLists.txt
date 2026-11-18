# G-12: JNI bridge to whisper.cpp (vendored, pinned tag: see third_party/whisper.cpp/VERSION.txt).
#
# whisper.cpp + ggml are built twice as static libraries (ExternalProject), each linked into its own
# small JNI library with identical exports:
#   whisper_v80 - baseline ARMv8.0 (runs on every arm64 phone, NFG-72)
#   whisper_v82 - ARMv8.2 + dotprod + fp16 (most phones since ~2019, e.g. Galaxy A15 / Helio G99)
# Kotlin (WhisperNative) reads the CPU features and loads exactly one of them.
# whisper.cpp is always compiled in Release mode: a Debug build of ggml is far too slow to use.
cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni C CXX)

include(ExternalProject)

get_filename_component(WHISPER_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp" ABSOLUTE)
if (NOT EXISTS "${WHISPER_SRC}/include/whisper.h")
    message(FATAL_ERROR "whisper.cpp source not found at ${WHISPER_SRC}")
endif ()

if (NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Whisper is built for arm64-v8a only (PLAN G-12); got ${ANDROID_ABI}")
endif ()

find_library(LOG_LIB log)

function(add_whisper_variant name arm_arch)
    set(prefix "${CMAKE_BINARY_DIR}/${name}")
    set(static_libs
        "${prefix}/lib/libwhisper.a"
        "${prefix}/lib/libggml.a"
        "${prefix}/lib/libggml-cpu.a"
        "${prefix}/lib/libggml-base.a")

    ExternalProject_Add(${name}_core
        SOURCE_DIR "${WHISPER_SRC}"
        BINARY_DIR "${prefix}/b"
        INSTALL_DIR "${prefix}"
        CMAKE_ARGS
            -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
            -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
            -DANDROID_ABI=${ANDROID_ABI}
            -DANDROID_PLATFORM=${ANDROID_PLATFORM}
            -DANDROID_STL=${ANDROID_STL}
            -DCMAKE_BUILD_TYPE=Release
            -DCMAKE_INSTALL_PREFIX=<INSTALL_DIR>
            -DCMAKE_INSTALL_LIBDIR=lib
            -DCMAKE_POSITION_INDEPENDENT_CODE=ON
            -DBUILD_SHARED_LIBS=OFF
            -DWHISPER_BUILD_TESTS=OFF
            -DWHISPER_BUILD_EXAMPLES=OFF
            -DWHISPER_BUILD_SERVER=OFF
            -DWHISPER_ALL_WARNINGS=OFF
            -DWHISPER_CURL=OFF
            -DWHISPER_SDL2=OFF
            -DGGML_NATIVE=OFF
            -DGGML_OPENMP=OFF
            -DGGML_BACKEND_DL=OFF
            -DGGML_CPU_KLEIDIAI=OFF
            -DGGML_CPU_ARM_ARCH=${arm_arch}
        BUILD_BYPRODUCTS ${static_libs}
    )

    add_library(${name} SHARED whisper_jni.cpp)
    add_dependencies(${name} ${name}_core)
    target_include_directories(${name} PRIVATE
        "${WHISPER_SRC}/include"
        "${WHISPER_SRC}/ggml/include")
    target_compile_features(${name} PRIVATE cxx_std_17)
    target_compile_options(${name} PRIVATE -O2 -fvisibility=hidden -Wall)
    target_link_libraries(${name} PRIVATE ${static_libs} ${LOG_LIB} m dl)
    # Do not re-export ggml/whisper symbols; 16 KB pages for Android 15+ devices.
    target_link_options(${name} PRIVATE
        -Wl,--exclude-libs,ALL
        -Wl,--gc-sections
        -Wl,-z,max-page-size=16384)
endfunction()

add_whisper_variant(whisper_v80 armv8-a)
add_whisper_variant(whisper_v82 armv8.2-a+dotprod+fp16)
