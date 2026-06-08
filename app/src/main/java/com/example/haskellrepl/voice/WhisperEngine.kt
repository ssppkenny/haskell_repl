package com.example.haskellrepl.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperEngine {

	private var ctxPtr: Long = 0

	private external fun nativeInit(modelPath: String): Long
	private external fun nativeTranscribe(ctxPtr: Long, samples: FloatArray, nSamples: Int): String
	private external fun nativeFree(ctxPtr: Long)

	companion object {
		init {
			System.loadLibrary("pty_bridge")
		}
	}

	fun init(modelPath: String): Result<Unit> = runCatching {
		ctxPtr = nativeInit(modelPath)
		if (ctxPtr == 0L) throw IllegalStateException("whisper init failed")
	}

	suspend fun transcribe(samples: ShortArray): String = withContext(Dispatchers.Default) {
		val floatSamples = FloatArray(samples.size) { samples[it] / 32768.0f }
		nativeTranscribe(ctxPtr, floatSamples, floatSamples.size)
	}

	fun free() {
		if (ctxPtr != 0L) {
			nativeFree(ctxPtr)
			ctxPtr = 0
		}
	}
}
