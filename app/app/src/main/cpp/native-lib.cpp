#include <jni.h>
#include <android/log.h>
#include <opencv2/imgproc.hpp>
#include <opencv2/core.hpp>

#define LOG_TAG "JNI-OPENCV"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

// -------------------------------------------------------------------------
// These functions are implemented in your OpenGL renderer (GLRenderer.cpp)
// Make sure these symbols exist there.
// -------------------------------------------------------------------------
extern "C" {
    void update_processed_from_gray(unsigned char* gray, int width, int height);
    void update_processed_from_rgba(unsigned char* rgba, int width, int height);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeProcessFrame(
        JNIEnv *env,
        jobject /* thiz */,
        jbyteArray frameData,
        jint width,
        jint height) {

    // -----------------------------
    // Convert Java NV21 to OpenCV
    // -----------------------------
    jbyte *nv21 = env->GetByteArrayElements(frameData, nullptr);
    if (!nv21) {
        LOGE("nv21 is null");
        return;
    }
    jsize len = env->GetArrayLength(frameData);

    Mat yuv(height + height/2, width, CV_8UC1, (unsigned char*)nv21);

    Mat bgr;
    try {
        cvtColor(yuv, bgr, COLOR_YUV2BGR_NV21);
    } catch (cv::Exception &e) {
        LOGE("cvtColor failed: %s", e.what());
        env->ReleaseByteArrayElements(frameData, nv21, JNI_ABORT);
        return;
    }

    // -------------------------------------
    // Convert to grayscale + apply Canny
    // -------------------------------------
    Mat gray;
    cvtColor(bgr, gray, COLOR_BGR2GRAY);

    Mat blurred;
    GaussianBlur(gray, blurred, Size(5,5), 1.5);

    Mat edges;
    double lowThresh = 50;
    double highThresh = 150;
    Canny(blurred, edges, lowThresh, highThresh);

    int edgePixels = countNonZero(edges);
    LOGD("Processed frame %dx%d - edge pixels=%d - bytes=%d",
         width, height, edgePixels, (int)len);

    // -------------------------------------------------------
    // 🔥 KEY ADDITION: Send the grayscale edge image to GL
    // -------------------------------------------------------
    update_processed_from_gray(edges.data, width, height);

    // cleanup
    env->ReleaseByteArrayElements(frameData, nv21, JNI_ABORT);
}
