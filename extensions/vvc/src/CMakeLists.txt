# libdav1d requires modern CMake.
cmake_minimum_required(VERSION 3.7.1 FATAL_ERROR)
set(CMAKE_CXX_STANDARD 11)
project(libdav1d C CXX)
include_directories(./
                    main/core-dav1d/jni/media/graphics
                    main/core-dav1d/jni/android
                    main/core-dav1d/jni/android/openglRender
                    main/core-dav1d/jni/dav1d/builddir/${ANDROID_ABI}
                    main/core-dav1d/jni/dav1d/builddir/${ANDROID_ABI}/include/dav1d
                    main/core-dav1d/jni/dav1d/include
                    main/core-dav1d/jni
                    )
#AUX_SOURCE_DIRECTORY(./ SOURCES)
file(GLOB SOURCES1 main/core-dav1d/jni/*.c*
        main/core-dav1d/jni/*/*.c*
        main/core-dav1d/jni/*/*/*.c*
        main/core-dav1d/jni/*/*/*/*.c*)
add_library(libdav1d
             SHARED
             ${SOURCES1}
             )

