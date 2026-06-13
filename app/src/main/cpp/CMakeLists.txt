cmake_minimum_required(VERSION 3.22.1)
project(silero_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ST_DIR ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch)

# SoundTouch 核心源码
file(GLOB SOUNDTOUCH_SRC ${ST_DIR}/src/*.cpp)

add_library(soundtouch_jni SHARED
        soundtouch_jni.cpp
        ${SOUNDTOUCH_SRC}
)

target_include_directories(soundtouch_jni PRIVATE
        ${ST_DIR}/include
        ${ST_DIR}/src
)

# 强制使用 32bit float 采样路径，关闭异常以外的行为保持默认
target_compile_definitions(soundtouch_jni PRIVATE
        SOUNDTOUCH_FLOAT_SAMPLES=1
)

# 性能优化
target_compile_options(soundtouch_jni PRIVATE -O3 -ffast-math)

find_library(log-lib log)
target_link_libraries(soundtouch_jni ${log-lib})
