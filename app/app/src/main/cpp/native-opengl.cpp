#include <jni.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <mutex>

#define LOG_TAG "GL-NATIVE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static unsigned char* processedBuffer = nullptr;
static int procWidth = 0;
static int procHeight = 0;

static std::mutex bufferMutex;

// ------------------------------------------------------------------------------------------
// Called from nativeProcessFrame() after Canny edges.
// It receives a grayscale image (1-byte per pixel)
// Converts to RGBA (required by GL) and stores it for GLRenderer.onDrawFrame().
// ------------------------------------------------------------------------------------------
extern "C"
void update_processed_from_gray(unsigned char* gray, int width, int height) {
    std::lock_guard<std::mutex> lock(bufferMutex);

    int sizeRGBA = width * height * 4;

    // allocate buffer if needed
    if (processedBuffer == nullptr ||
        procWidth != width || procHeight != height) {

        if (processedBuffer) free(processedBuffer);
        processedBuffer = (unsigned char*) malloc(sizeRGBA);

        procWidth = width;
        procHeight = height;
    }

    // convert grayscale → RGBA
    for (int i = 0; i < width * height; i++) {
        unsigned char p = gray[i];
        processedBuffer[i * 4 + 0] = p;  // R
        processedBuffer[i * 4 + 1] = p;  // G
        processedBuffer[i * 4 + 2] = p;  // B
        processedBuffer[i * 4 + 3] = 255; // A
    }
}

// ------------------------------------------------------------------------------------------
// Java calls this every frame to get RGBA buffer.
// ------------------------------------------------------------------------------------------
extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeGetProcessedFrameBuffer(
        JNIEnv* env, jclass clazz) {

    std::lock_guard<std::mutex> lock(bufferMutex);

    if (!processedBuffer || procWidth == 0 || procHeight == 0)
        return nullptr;

    return env->NewDirectByteBuffer(processedBuffer,
                                    procWidth * procHeight * 4);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeGetProcessedFrameWidth(
        JNIEnv* env, jclass clazz) {
    return procWidth;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeGetProcessedFrameHeight(
        JNIEnv* env, jclass clazz) {
    return procHeight;
}
// ------------------------------------------------------------------------------------------
// EMPTY GL LIFECYCLE STUBS (REQUIRED BY Java SIDE)
// ------------------------------------------------------------------------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeInitGL(
        JNIEnv* env, jclass clazz) {
    // Nothing needed yet. Renderer handles GL creation.
    LOGD("nativeInitGL() called");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeDestroyGL(
        JNIEnv* env, jclass clazz) {
    LOGD("nativeDestroyGL() called");

    std::lock_guard<std::mutex> lock(bufferMutex);

    if (processedBuffer) {
        free(processedBuffer);
        processedBuffer = nullptr;
    }

    procWidth = 0;
    procHeight = 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeOnResume(
        JNIEnv* env, jclass clazz) {
    LOGD("nativeOnResume()");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeOnPause(
        JNIEnv* env, jclass clazz) {
    LOGD("nativeOnPause()");
}
