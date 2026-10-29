# The five WebP libraries ffmpeg loads, as shared libraries named like Termux's
# (libsharpyuv.so, libwebp.so -> libsharpyuv.so, libwebpdecoder.so, libwebpdemux.so and
# libwebpmux.so -> libwebp.so), linked for 16 KB pages. Source lists: sources.mk.
LOCAL_PATH := $(WEBP_SRC)
include $(BUILD_DIR)/sources.mk

WEBP_CFLAGS := -Wall -DANDROID -DHAVE_MALLOC_H -DHAVE_PTHREAD -DWEBP_USE_THREAD -fvisibility=hidden \
    -O2 -ffunction-sections -fdata-sections
WEBP_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,--gc-sections

include $(CLEAR_VARS)
LOCAL_MODULE := sharpyuv
LOCAL_SRC_FILES := $(sharpyuv_srcs)
LOCAL_C_INCLUDES := $(WEBP_SRC)
LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_LDFLAGS := $(WEBP_LDFLAGS)
LOCAL_LDLIBS := -lm
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := webpdecoder
LOCAL_SRC_FILES := $(dec_srcs) $(dsp_dec_srcs) $(utils_dec_srcs)
LOCAL_C_INCLUDES := $(WEBP_SRC) $(WEBP_SRC)/src
LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_LDFLAGS := $(WEBP_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := webp
LOCAL_SRC_FILES := $(dec_srcs) $(dsp_dec_srcs) $(utils_dec_srcs) $(dsp_enc_srcs) $(enc_srcs) $(utils_enc_srcs)
LOCAL_C_INCLUDES := $(WEBP_SRC) $(WEBP_SRC)/src
LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_LDFLAGS := $(WEBP_LDFLAGS)
LOCAL_LDLIBS := -lm
LOCAL_SHARED_LIBRARIES := sharpyuv
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := webpdemux
LOCAL_SRC_FILES := $(demux_srcs)
LOCAL_C_INCLUDES := $(WEBP_SRC) $(WEBP_SRC)/src
LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_LDFLAGS := $(WEBP_LDFLAGS)
LOCAL_SHARED_LIBRARIES := webp
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := webpmux
LOCAL_SRC_FILES := $(mux_srcs)
LOCAL_C_INCLUDES := $(WEBP_SRC) $(WEBP_SRC)/src
LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_LDFLAGS := $(WEBP_LDFLAGS)
LOCAL_SHARED_LIBRARIES := webp
include $(BUILD_SHARED_LIBRARY)
