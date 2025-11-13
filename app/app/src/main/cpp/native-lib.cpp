#include <jni.h>
#include <android/log.h>

#define LOG_TAG "JNI-CPP"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__);

extern "C"
JNIEXPORT void JNICALL
Java_com_example_real_1time_1edge_1detection_1viewer_MainActivity_nativeProcessFrame(
        JNIEnv *env,
        jobject thiz,
        jbyteArray frameData,
        jint width,
        jint height) {

    jbyte *data = env->GetByteArrayElements(frameData, nullptr);
    int length = env->GetArrayLength(frameData);

    LOGD("Received frame in C++: %dx%d, %d bytes", width, height, length);

    // IMPORTANT: release memory back to JVM
    env->ReleaseByteArrayElements(frameData, data, JNI_ABORT);
}
