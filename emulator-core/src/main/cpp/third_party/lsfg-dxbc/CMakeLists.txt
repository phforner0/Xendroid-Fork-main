cmake_minimum_required(VERSION 3.10)

project(lsfg-dxbc
    VERSION 2.6.2
    DESCRIPTION "DXVK's DXBC"
    LANGUAGES CXX)

file(GLOB SOURCES
    "src/dxbc/*.cpp"
    "src/spirv/*.cpp"
    "src/util/*.cpp"
)

add_library(lsfg-dxbc STATIC ${SOURCES})

# target
set_target_properties(lsfg-dxbc PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON)
target_include_directories(lsfg-dxbc SYSTEM
    PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/../../xenia/third_party/Vulkan-Headers/include"
    PUBLIC include/dxbc
    PUBLIC include/spirv include/util include/dxvk)
target_compile_options(lsfg-dxbc PRIVATE
    -fPIC)

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set_target_properties(lsfg-dxbc PROPERTIES
        EXPORT_COMPILE_COMMANDS ON)
endif()
