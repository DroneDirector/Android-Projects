// JNI bridge for RNNoise (voice isolation). Written for VeyroCut Pro.
#include <jni.h>
#include <stdlib.h>
#include "rnnoise.h"

#define FRAME 480

JNIEXPORT jlong JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeCreate(JNIEnv *env, jclass clazz) {
    return (jlong)(intptr_t) rnnoise_create(NULL);
}

JNIEXPORT void JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDestroy(JNIEnv *env, jclass clazz, jlong h) {
    if (h) rnnoise_destroy((DenoiseState *)(intptr_t) h);
}

// in / out: 480 samples at 48 kHz in the 16-bit range (-32768..32767). Returns voice probability.
JNIEXPORT jfloat JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeProcess(JNIEnv *env, jclass clazz, jlong h, jfloatArray in, jfloatArray out) {
    if (!h) return 0.f;
    float buf_in[FRAME], buf_out[FRAME];
    (*env)->GetFloatArrayRegion(env, in, 0, FRAME, buf_in);
    float vad = rnnoise_process_frame((DenoiseState *)(intptr_t) h, buf_out, buf_in);
    (*env)->SetFloatArrayRegion(env, out, 0, FRAME, buf_out);
    return vad;
}
