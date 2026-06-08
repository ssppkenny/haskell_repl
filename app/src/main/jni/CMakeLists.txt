cmake_minimum_required(VERSION 3.22.1)
project("haskellrepl-pty")

add_library(pty_bridge SHARED pty_bridge.c)

find_library(log-lib log)
target_link_libraries(pty_bridge ${log-lib})

target_compile_options(pty_bridge PRIVATE -Wall -Werror)
