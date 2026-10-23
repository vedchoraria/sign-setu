cmake_minimum_required(VERSION 3.22.1)
project(assistant LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp (MIT) v0.5.0, vendored in third_party/llama.cpp so configuring needs no network (SEC-50).
# The commit is fixed here because the build-info script would otherwise read this repository's history.
set(LLAMA_BUILD_NUMBER 0 CACHE STRING "" FORCE)
set(LLAMA_BUILD_COMMIT "7fe450e" CACHE STRING "" FORCE)
set(GGML_BUILD_COMMIT "7fe450e" CACHE STRING "" FORCE)
set(GIT_EXE "GIT_EXE-NOTFOUND" CACHE FILEPATH "Prevent git execution in vendored subdirectories" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
# Every ABI stays on its baseline instruction set: the universal APK also reaches x86_64 tablets without AVX2,
# where forced AVX2 code would crash on load. ponytail: per-CPU variants (GGML_CPU_ALL_VARIANTS) if speed matters.
add_subdirectory(third_party/llama.cpp)

add_library(assistant SHARED assistant.cpp)
target_link_libraries(assistant llama log)
# Android 15 and later need native libraries aligned to 16 KB pages.
target_link_options(assistant PRIVATE "-Wl,-z,max-page-size=16384")
