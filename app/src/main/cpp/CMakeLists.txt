# On-phone AI: whisper.cpp for speech to text and llama.cpp for summaries (both MIT licence),
# sharing one copy of their ggml engine. Built for each ABI.
cmake_minimum_required(VERSION 3.22.1)
project(jotter_speech C CXX)

set(CMAKE_CXX_STANDARD 17)
if (NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
    # Even debug builds need an optimised model runner to be usable.
    set(CMAKE_BUILD_TYPE Release)
endif ()

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# One CPU backend per instruction set (plain ARMv8 up to ARMv9 with SME); the fastest one this phone
# supports is picked when transcription starts.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

include(FetchContent)
# Both released on 10 September 2026 with ggml 0.23.0. llama.cpp comes first so its ggml is the one
# built; whisper.cpp then uses it instead of its own copy.
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG 50182a53fa2c26bd2a7fc31d855231effdc2f4ad # b10900
)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG 927cfce34f31707e17f2bff35c349632fb9e2c3a # v1.9.4
)
FetchContent_MakeAvailable(llama whisper)

add_library(jotter_speech SHARED speech.cpp summary.cpp)
target_link_libraries(jotter_speech PRIVATE whisper llama ggml)
if (ANDROID)
    target_link_libraries(jotter_speech PRIVATE log)
endif ()
