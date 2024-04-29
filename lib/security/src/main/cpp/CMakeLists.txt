cmake_minimum_required(VERSION 3.10.2)

set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

project("SodiumLib")

find_package(sodium REQUIRED CONFIG)

add_library(libsodium SHARED sodium_lib.cpp)

find_library(log-lib log)

target_link_libraries(libsodium sodium::sodium ${log-lib})
