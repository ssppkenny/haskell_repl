#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <time.h>
#include <android/log.h>

#include "whisper.h"

#define TAG "HaskellReplPty"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static double get_time_ms(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ts.tv_sec * 1000.0 + ts.tv_nsec / 1000000.0;
}

JNIEXPORT jobject JNICALL
Java_com_example_haskellrepl_service_PtyBridge_nativeOpenPty(
	JNIEnv *env, jclass clazz) {

	int masterFd;
	char slaveName[256];

	masterFd = posix_openpt(O_RDWR | O_NOCTTY);
	if (masterFd < 0) {
		LOGE("posix_openpt failed: %s", strerror(errno));
		return NULL;
	}

	if (grantpt(masterFd) != 0) {
		LOGE("grantpt failed: %s", strerror(errno));
		close(masterFd);
		return NULL;
	}

	if (unlockpt(masterFd) != 0) {
		LOGE("unlockpt failed: %s", strerror(errno));
		close(masterFd);
		return NULL;
	}

	char *ptsName = ptsname(masterFd);
	if (ptsName == NULL) {
		LOGE("ptsname failed: %s", strerror(errno));
		close(masterFd);
		return NULL;
	}
	strncpy(slaveName, ptsName, sizeof(slaveName) - 1);
	slaveName[sizeof(slaveName) - 1] = '\0';

	jstring slaveNameStr = (*env)->NewStringUTF(env, slaveName);

	jclass fdClass = (*env)->FindClass(env, "java/io/FileDescriptor");
	jmethodID fdCtor = (*env)->GetMethodID(env, fdClass, "<init>", "()V");
	jobject fdObj = (*env)->NewObject(env, fdClass, fdCtor);

	jfieldID fdField = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
	(*env)->SetIntField(env, fdObj, fdField, masterFd);

	jclass resultClass = (*env)->FindClass(
		env, "com/example/haskellrepl/service/PtyBridge$PtyResult");
	jmethodID resultCtor = (*env)->GetMethodID(
		env, resultClass, "<init>", "(Ljava/io/FileDescriptor;Ljava/lang/String;)V");

	jobject result = (*env)->NewObject(
		env, resultClass, resultCtor, fdObj, slaveNameStr);

	LOGD("PTY opened: masterFd=%d, slave=%s", masterFd, slaveName);
	return result;
}

JNIEXPORT void JNICALL
Java_com_example_haskellrepl_service_PtyBridge_nativeSetWinSize(
	JNIEnv *env, jclass clazz, jint masterFd, jint rows, jint cols) {

	struct winsize ws;
	ws.ws_row = (unsigned short) rows;
	ws.ws_col = (unsigned short) cols;
	ws.ws_xpixel = 0;
	ws.ws_ypixel = 0;

	if (ioctl(masterFd, TIOCSWINSZ, &ws) != 0) {
		LOGE("ioctl TIOCSWINSZ failed: %s", strerror(errno));
	}
}

JNIEXPORT jlong JNICALL
Java_com_example_haskellrepl_voice_WhisperEngine_nativeInit(
	JNIEnv *env, jobject thiz, jstring modelPath) {

	const char *path = (*env)->GetStringUTFChars(env, modelPath, NULL);
	struct whisper_context_params cparams = whisper_context_default_params();
	double t0 = get_time_ms();
	struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
	double t1 = get_time_ms();
	LOGD("whisper init: %.0f ms", t1 - t0);
	(*env)->ReleaseStringUTFChars(env, modelPath, path);
	if (ctx == NULL) {
		LOGE("whisper_init failed");
		return 0;
	}

	LOGD("whisper model loaded: %s", path);
	int has_neon = 0, has_dp = 0;
#if defined(__ARM_NEON)
	has_neon = 1;
#endif
#if defined(__ARM_FEATURE_DOTPROD)
	has_dp = 1;
#endif
	LOGD("ARM features at compile time: NEON=%d DOTPROD=%d ARCH=%d",
		has_neon, has_dp,
#if defined(__ARM_ARCH)
		__ARM_ARCH
#else
		0
#endif
	);
	return (jlong) ctx;
}

JNIEXPORT jstring JNICALL
Java_com_example_haskellrepl_voice_WhisperEngine_nativeTranscribe(
	JNIEnv *env, jobject thiz, jlong ctxPtr, jfloatArray samples, jint nSamples) {

	struct whisper_context *ctx = (struct whisper_context *) ctxPtr;
	if (ctx == NULL) return (*env)->NewStringUTF(env, "");

	jfloat *data = (*env)->GetFloatArrayElements(env, samples, NULL);

	struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
	params.print_progress = false;
	params.print_special = false;
	params.print_realtime = false;
	params.print_timestamps = false;
	params.no_timestamps = true;
	params.single_segment = true;
	params.language = "en";
	params.n_threads = 4;
	params.n_max_text_ctx = 0;

	double t0 = get_time_ms();
	whisper_full(ctx, params, data, nSamples);
	double t1 = get_time_ms();
	LOGD("whisper_full: %.0f ms for %d samples (%.1f sec audio)",
		t1 - t0, nSamples, (double)nSamples / 16000.0);
	(*env)->ReleaseFloatArrayElements(env, samples, data, JNI_ABORT);

	char result[4096];
	result[0] = '\0';
	int n_segments = whisper_full_n_segments(ctx);
	for (int i = 0; i < n_segments; i++) {
		const char *text = whisper_full_get_segment_text(ctx, i);
		if (text != NULL && text[0] != '\0') {
			if (i > 0) strcat(result, " ");
			strcat(result, text);
		}
	}
	LOGD("whisper transcription: %s", result);
	return (*env)->NewStringUTF(env, result);
}

JNIEXPORT void JNICALL
Java_com_example_haskellrepl_voice_WhisperEngine_nativeFree(
	JNIEnv *env, jobject thiz, jlong ctxPtr) {

	struct whisper_context *ctx = (struct whisper_context *) ctxPtr;
	if (ctx != NULL) {
		whisper_free(ctx);
		LOGD("whisper context freed");
	}
}
