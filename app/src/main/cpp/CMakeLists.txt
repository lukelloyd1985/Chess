cmake_minimum_required(VERSION 3.22.1)
project(stockfish CXX)

# Stockfish 19 (GPLv3), built as a standalone executable. It is named
# libstockfish.so so Gradle packages it into the APK's lib/<abi>/ directory,
# from where the app launches it as a child process (see StockfishEngine.kt).
#
# The NNUE network is NOT embedded; the app ships it as an asset and loads it
# with the EvalFile UCI option (see the downloadNnue task in app/build.gradle.kts).
set(SF "${CMAKE_CURRENT_SOURCE_DIR}/stockfish")
file(GLOB SF_SOURCES
    "${SF}/*.cpp"
    "${SF}/nnue/*.cpp"
    "${SF}/nnue/features/*.cpp"
    "${SF}/syzygy/*.cpp")

add_executable(stockfish ${SF_SOURCES})
target_include_directories(stockfish PRIVATE "${SF}")

set_target_properties(stockfish PROPERTIES
    OUTPUT_NAME "stockfish"
    PREFIX "lib"
    SUFFIX ".so"
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
    POSITION_INDEPENDENT_CODE ON
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

target_compile_definitions(stockfish PRIVATE NDEBUG NNUE_EMBEDDING_OFF IS_64BIT)
target_compile_options(stockfish PRIVATE -O3 -fno-exceptions)
target_link_options(stockfish PRIVATE -pie)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64" OR ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(stockfish PRIVATE USE_NEON=8)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64" OR ANDROID_ABI STREQUAL "x86_64")
    target_compile_definitions(stockfish PRIVATE USE_POPCNT USE_SSE41 USE_SSSE3 USE_SSE2)
    target_compile_options(stockfish PRIVATE -msse4.1 -mpopcnt)
endif()
