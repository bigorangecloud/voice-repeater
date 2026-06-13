////////////////////////////////////////////////////////////////////////////////
// 自定义 SoundTouch JNI：在内存中处理 16-bit PCM short 数组
// 输入 short[] -> 内部转 float -> SoundTouch 处理 -> 转回 short[]
////////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring>

#include "SoundTouch.h"

#define LOG_TAG "SoundTouchJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace soundtouch;

#define JNI_FUNC(name) Java_com_example_silero_audio_SoundTouch_##name

extern "C" {

JNIEXPORT jlong JNICALL
JNI_FUNC(newInstance)(JNIEnv *env, jobject thiz) {
    return (jlong) (new SoundTouch());
}

JNIEXPORT void JNICALL
JNI_FUNC(deleteInstance)(JNIEnv *env, jobject thiz, jlong handle) {
    SoundTouch *st = (SoundTouch *) handle;
    delete st;
}

JNIEXPORT void JNICALL
JNI_FUNC(setSampleRate)(JNIEnv *env, jobject thiz, jlong handle, jint rate) {
    ((SoundTouch *) handle)->setSampleRate((uint) rate);
}

JNIEXPORT void JNICALL
JNI_FUNC(setChannels)(JNIEnv *env, jobject thiz, jlong handle, jint channels) {
    ((SoundTouch *) handle)->setChannels((uint) channels);
}

JNIEXPORT void JNICALL
JNI_FUNC(setTempo)(JNIEnv *env, jobject thiz, jlong handle, jfloat tempo) {
    ((SoundTouch *) handle)->setTempo(tempo);
}

JNIEXPORT void JNICALL
JNI_FUNC(setRate)(JNIEnv *env, jobject thiz, jlong handle, jfloat rate) {
    ((SoundTouch *) handle)->setRate(rate);
}

JNIEXPORT void JNICALL
JNI_FUNC(setPitchSemiTones)(JNIEnv *env, jobject thiz, jlong handle, jfloat pitch) {
    ((SoundTouch *) handle)->setPitchSemiTones(pitch);
}

// 处理整段 PCM：输入 short[]，返回处理后的 short[]
JNIEXPORT jshortArray JNICALL
JNI_FUNC(process)(JNIEnv *env, jobject thiz, jlong handle, jshortArray input) {
    SoundTouch *st = (SoundTouch *) handle;

    jsize inLen = env->GetArrayLength(input);
    if (inLen <= 0) {
        return env->NewShortArray(0);
    }

    jshort *inBuf = env->GetShortArrayElements(input, nullptr);

    // short -> float (归一化到 [-1, 1])
    std::vector<float> fbuf(inLen);
    const float INV = 1.0f / 32768.0f;
    for (jsize i = 0; i < inLen; i++) {
        fbuf[i] = (float) inBuf[i] * INV;
    }
    env->ReleaseShortArrayElements(input, inBuf, JNI_ABORT);

    // 全部喂入并 flush
    st->putSamples(fbuf.data(), (uint) inLen);
    st->flush();

    // 收集输出
    std::vector<float> outF;
    const int CHUNK = 4096;
    float tmp[CHUNK];
    int n;
    do {
        n = st->receiveSamples(tmp, CHUNK);
        if (n > 0) {
            outF.insert(outF.end(), tmp, tmp + n);
        }
    } while (n != 0);

    // float -> short（带 clip）
    jsize outLen = (jsize) outF.size();
    jshortArray result = env->NewShortArray(outLen);
    if (outLen > 0) {
        std::vector<jshort> outS(outLen);
        for (jsize i = 0; i < outLen; i++) {
            float v = outF[i] * 32768.0f;
            if (v > 32767.0f) v = 32767.0f;
            if (v < -32768.0f) v = -32768.0f;
            outS[i] = (jshort) v;
        }
        env->SetShortArrayRegion(result, 0, outLen, outS.data());
    }
    return result;
}

} // extern "C"
