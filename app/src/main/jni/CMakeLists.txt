cmake_minimum_required(VERSION 3.22.1)
project("haskellrepl-pty")

add_library(pty_bridge SHARED pty_bridge.c)

find_library(log-lib log)
target_link_libraries(pty_bridge ${log-lib})

target_compile_options(pty_bridge PRIVATE -Wall -Werror)

# whisper.cpp integration
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "")
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "")
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "")
set(GGML_ACCELERATE OFF CACHE BOOL "")
set(GGML_NATIVE OFF CACHE BOOL "")
set(GGML_OPENMP OFF CACHE BOOL "")
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "")
set(GGML_BACKEND_DL OFF CACHE BOOL "")
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod -O3")
add_subdirectory(${WHISPER_DIR} whisper_build)

target_compile_definitions(ggml-cpu PRIVATE
	GGML_USE_DOTPROD
	GGML_USE_FP16_VECTOR_ARITHMETIC
	GGML_USE_MATMUL_INT8)
target_link_libraries(pty_bridge whisper)

target_link_libraries(pty_bridge whisper)
target_compile_definitions(pty_bridge PRIVATE GGML_USE_CPU)
