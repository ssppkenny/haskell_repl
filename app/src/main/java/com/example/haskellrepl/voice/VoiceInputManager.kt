package com.example.haskellrepl.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceInputManager(private val context: android.content.Context) {

	sealed class State {
		data object Idle : State()
		data object Recording : State()
		data object Transcribing : State()
		data class Result(val text: String) : State()
		data class Error(val message: String) : State()
		data class Downloading(val progress: Int) : State()
	}

	private val _state = MutableStateFlow<State>(State.Idle)
	val state: StateFlow<State> = _state

	private val downloader = ModelDownloader(context)
	private var engine: WhisperEngine? = null
	private var audioRecord: AudioRecord? = null
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var initialized = false
	private var isRecording = false

	private val sampleRate = 16000
	private val channelConfig = AudioFormat.CHANNEL_IN_MONO
	private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

	private val wordMap = mapOf(
		"equals" to "=",
		"equal" to "=",
		"arrow" to "->",
		"colon" to "::",
		"right arrow" to "->",
		"left arrow" to "<-",
		"double colon" to "::",
		"lambda" to "\\",
		"backslash" to "\\",
		"fat arrow" to "=>",
		"double arrow" to "=>",
		"pipe" to "|",
		"dollar" to "$",
		"dot dot" to "..",
		"underscore" to "_",
		"plus plus" to "++",
		"minus minus" to "--",
		"less than" to "<",
		"greater than" to ">",
		"open bracket" to "[",
		"close bracket" to "]",
		"open paren" to "(",
		"close paren" to ")",
		"comma" to ",",
		"period" to ".",
	)

	fun hasPermission(): Boolean =
		ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
			PackageManager.PERMISSION_GRANTED

	suspend fun ensureReady(): Result<Unit> {
		if (initialized) return Result.success(Unit)

		_state.value = State.Downloading(0)
		val dlResult = downloader.downloadIfNeeded { pct ->
			_state.value = State.Downloading(pct)
		}
		if (dlResult.isFailure) {
			val msg = "Model download failed: ${dlResult.exceptionOrNull()?.message}"
			_state.value = State.Error(msg)
			delay(2000)
			_state.value = State.Idle
			return Result.failure(dlResult.exceptionOrNull()!!)
		}

		engine = WhisperEngine()
		val initResult = engine!!.init(downloader.getModelPath())
		if (initResult.isFailure) {
			val msg = "Whisper init failed: ${initResult.exceptionOrNull()?.message}"
			_state.value = State.Error(msg)
			delay(2000)
			_state.value = State.Idle
			return initResult
		}

		initialized = true
		return Result.success(Unit)
	}

	fun startRecording() {
		if (!hasPermission()) {
			_state.value = State.Error("Microphone permission not granted")
			return
		}
		if (isRecording) return
		isRecording = true

		scope.launch {
			val ready = ensureReady()
			if (ready.isFailure) {
				isRecording = false
				return@launch
			}

			_state.value = State.Recording

			val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
			val record = AudioRecord(
				MediaRecorder.AudioSource.MIC,
				sampleRate, channelConfig, audioFormat,
				bufferSize * 4
			)
			audioRecord = record

			val allSamples = mutableListOf<Short>()
			record.startRecording()

			val buffer = ShortArray(bufferSize)
			val startTime = System.currentTimeMillis()
			val maxRecordMs = 10_000L
			try {
				while (isActive && isRecording && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
					val read = record.read(buffer, 0, buffer.size)
					if (read > 0) {
						for (i in 0 until read) {
							allSamples.add(buffer[i])
						}
					}
					if (System.currentTimeMillis() - startTime > maxRecordMs) {
						isRecording = false
						break
					}
				}
			} catch (_: Exception) { }
			finally {
				try { record.stop() } catch (_: Exception) { }
				try { record.release() } catch (_: Exception) { }
				audioRecord = null
			}

			val samples = allSamples.toShortArray()
			isRecording = false

			if (samples.isNotEmpty() && engine != null) {
				_state.value = State.Transcribing
				val raw = engine!!.transcribe(samples)
				val text = postProcess(raw)
				if (text.isNotBlank()) {
					_state.value = State.Result(text)
				} else {
					_state.value = State.Idle
				}
			} else {
				_state.value = State.Idle
			}
		}
	}

	private fun postProcess(text: String): String {
		if (text.isBlank()) return text

		var result = text.trim()
			.replace(Regex("""[.!?,;:]$"""), "")
			.lowercase()

		for ((word, symbol) in wordMap) {
			result = result.replace(word, symbol)
		}

		result = result
			.replace(Regex("""\s+"""), " ")
			.trim()

		return result
	}

	fun stopRecording() {
		isRecording = false
		audioRecord?.let {
			try { it.stop() } catch (_: Exception) { }
		}
	}

	fun clearResult() {
		if (_state.value is State.Result) {
			_state.value = State.Idle
		}
	}

	fun destroy() {
		isRecording = false
		audioRecord?.let {
			try { it.release() } catch (_: Exception) { }
		}
		engine?.free()
		scope.cancel()
	}
}
