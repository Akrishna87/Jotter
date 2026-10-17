# Speech to text on the phone: whisper.cpp (MIT licence), built for each ABI.
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

include(FetchContent)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG 48f628a84833905ee4a0658ee6d4a5c915ce1997 # v1.8.7
)
FetchContent_MakeAvailable(whisper)

add_library(jotter_speech SHARED speech.cpp)
target_link_libraries(jotter_speech PRIVATE whisper ggml)
if (ANDROID)
    target_link_libraries(jotter_speech PRIVATE log)
endif ()
