MY_DIR := $(call my-dir)

# libo266dec
include $(CLEAR_VARS)
LOCAL_PATH  := $(MY_DIR)/../../jniLibs/$(TARGET_ARCH_ABI)
LOCAL_MODULE    := o266dec
LOCAL_SRC_FILES := libo266dec.a
include $(PREBUILT_STATIC_LIBRARY)

#
# MXDav1dDec
#
include $(CLEAR_VARS)
LOCAL_PATH := $(MY_DIR)
SRC_PATH := $(MY_DIR)

LOCAL_C_INCLUDES := \
$(SRC_PATH) \
$(LOCAL_PATH)/../../../../../dav1d/src/main/core-dav1d/jni/media/graphics \
$(LOCAL_PATH)/../../../../../dav1d/src/main/core-dav1d/jni/android \
$(LOCAL_PATH)/../../../../../dav1d/src/main/core-dav1d/jni/android/openglRender \
$(LOCAL_PATH)/libvvc \
$(LOCAL_PATH)/libvvc/api

LOCAL_SRC_FILES := \
$(SRC_PATH)/android/vvc1_jni.cpp

LOCAL_CFLAGS := \
-Wno-multichar \
-DPROJECT=\"*MX\" \
-D_LARGEFILE_SOURCE \
-DHAVE_AV_CONFIG_H \
-DPIC \
-DFF_API_AVPICTURE=1 \
-DANDROID_BITMAP_RESULT_SUCCESS=0 \
-DMXTECHS \
-DMX=1 \
-DFORMAT_STRING_FFMPEG_SUPPORT \
-DCHECK_PACKET_DATA


ifneq ($(OLD_FFMPEG),1)
	LOCAL_CFLAGS += -DNEW_AVSUBTITLERECT -DNEW_FFMPEG
endif

# -include ../rename_symbols.h
# -save-temps : to save assembly output file.


LOCAL_CPPFLAGS := \
-std=gnu++11 \
-Wno-invalid-offsetof \
-D_GLIBCXX_USE_C99_STDINT_TR1 \
-D__STDC_CONSTANT_MACROS \
-D__STDC_LIMIT_MACROS


ifeq ($(findstring clang, $(NDK_TOOLCHAIN_VERSION)),)
	# GCC only.
	LOCAL_CPPFLAGS += \
	-Wno-literal-suffix
else
	# Clang only.
	LOCAL_CFLAGS += \
	-Wno-reserved-user-defined-literal \
	-Wno-deprecated-declarations \
	-Wno-gnu-designator
endif


LOCAL_CPP_FEATURES := exceptions rtti


ifeq ($(APP_OPTIM),debug)
	LOCAL_CPP_FEATURES += rtti
	LOCAL_SRC_FILES += \
	$(SRC_PATH)/share/Log.Redirect.cpp \
	$(SRC_PATH)/share/FileLog.cpp
	# LOCAL_CFLAGS += -DTEST
endif

LOCAL_LDLIBS := \
-lz \
-ldl \
-llog \
-landroid \
-lEGL \
-lGLESv2 \
-lOpenSLES \
-lmxav1dec \
-L$(ANDROID_SDKS_LIBRARY_PATH)/$(LINK_AGAINST) \
-L$(NDK_APP_DST_DIR) \
-L$(LOCAL_PATH)/../../../../../dav1d/src/main/jniLibs/$(TARGET_ARCH_ABI)

include $(LOCAL_PATH)/a-arch-$(TARGET_ARCH).mk

########### Temporary ##########
# LOCAL_CFLAGS += -DNDEBUG -UDEBUG
################################

LOCAL_ASFLAGS := $(LOCAL_CFLAGS)
LOCAL_STATIC_LIBRARIES := o266dec

# Don't strip debug builds
ifeq ($(APP_OPTIM),debug)
    cmd-strip := 
endif

include $(BUILD_SHARED_LIBRARY)
