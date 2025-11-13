cmake_minimum_required(VERSION 3.10.2)

project("native-lib")

# ───────────────────────────────────────────
# 1) Add your native source files
# ───────────────────────────────────────────
add_library(native-lib SHARED
        native-lib.cpp
        native-opengl.cpp
)

# ───────────────────────────────────────────
# 2) Include OpenCV headers
# (they are inside cpp/include/)
# ───────────────────────────────────────────
target_include_directories(native-lib PRIVATE
        ${CMAKE_SOURCE_DIR}/include
)

# ───────────────────────────────────────────
# 3) Import OpenCV shared lib (.so)
#    from jniLibs/<ABI>/libopencv_java4.so
# ───────────────────────────────────────────
add_library(opencv_java4 SHARED IMPORTED)

set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so
)

# ───────────────────────────────────────────
# 4) Import GLES2
# ───────────────────────────────────────────
find_library(GLES-lib GLESv2)

# ───────────────────────────────────────────
# 5) Import logcat lib
# ───────────────────────────────────────────
find_library(log-lib log)

# ───────────────────────────────────────────
# 6) Link libc++_shared (PREVENT missing .so crash)
# ───────────────────────────────────────────
find_library(cpp_shared-lib c++_shared)

# ───────────────────────────────────────────
# 7) Final linking
# ───────────────────────────────────────────
target_link_libraries(native-lib
        opencv_java4
        ${GLES-lib}
        ${log-lib}
        ${cpp_shared-lib}
)
