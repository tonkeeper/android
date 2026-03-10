cmake_minimum_required(VERSION 3.10.2)

set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

project("SodiumLib")

find_package(sodium REQUIRED CONFIG)

add_library(sodium_jni SHARED sodium_lib.cpp)

find_library(log-lib log)

target_link_libraries(sodium_jni sodium::sodium ${log-lib})

target_link_options(sodium_jni PRIVATE -s)

add_custom_command(TARGET sodium_jni POST_BUILD
        COMMAND ${CMAKE_STRIP} $<TARGET_FILE:sodium_jni>
        COMMAND ${CMAKE_STRIP} $<TARGET_LINKER_FILE:sodium::sodium> || true
        COMMENT "Stripping libraries"
)