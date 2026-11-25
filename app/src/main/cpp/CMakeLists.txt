cmake_minimum_required(VERSION 3.22.1)

project(onestate_native LANGUAGES CXX)

add_library(onestate_native SHARED native_aob.cpp)

target_compile_features(onestate_native PRIVATE cxx_std_17)
target_compile_options(onestate_native PRIVATE -Wall -Wextra -Werror)

find_library(log-lib log)
target_link_libraries(onestate_native ${log-lib})
