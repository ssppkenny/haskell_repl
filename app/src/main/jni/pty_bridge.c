#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <android/log.h>

#define TAG "HaskellReplPty"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

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
