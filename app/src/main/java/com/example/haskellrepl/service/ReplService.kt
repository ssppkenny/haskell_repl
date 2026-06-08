package com.example.haskellrepl.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.haskellrepl.extract.GhciExtractor
import com.example.haskellrepl.extract.ExtractionResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ReplService : Service() {

	inner class LocalBinder : Binder() {
		fun getService(): ReplService = this@ReplService
	}

	private val binder = LocalBinder()
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	private var ghciProcess: GHCiProcess? = null
	private var healthCheckJob: Job? = null
	private var ghciDir: java.io.File? = null

	private val _state = MutableStateFlow<ReplState>(ReplState.Extracting)
	val state: StateFlow<ReplState> = _state.asStateFlow()

	private val _output = MutableSharedFlow<ReplOutput>(replay = 50)
	val output: SharedFlow<ReplOutput> = _output.asSharedFlow()

	sealed class ReplState {
		data object Extracting : ReplState()
		data class ExtractProgress(val current: Int, val total: Int, val message: String) : ReplState()
		data object Starting : ReplState()
		data object Ready : ReplState()
		data class Error(val message: String) : ReplState()
		data class Prompt(val module: String, val isContinuation: Boolean) : ReplState()
	}

	override fun onBind(intent: Intent?): IBinder = binder

	override fun onCreate() {
		super.onCreate()
		startForeground(NOTIFICATION_ID, createNotification("Setting up..."))
		initialize()
	}

	override fun onDestroy() {
		healthCheckJob?.cancel()
		ghciProcess?.shutdown()
		scope.cancel()
		super.onDestroy()
	}

	fun sendExpression(expression: String) {
		ghciProcess?.sendCommand(expression)
	}

	fun interrupt() {
		ghciProcess?.interrupt()
	}

	fun restart() {
		ghciProcess?.shutdown()
		ghciDir?.let { dir -> scope.launch { startGHCi(dir) } }
			?: scope.launch { initialize() }
	}

	private fun initialize() {
		scope.launch {
			_state.value = ReplState.Extracting
			val extractor = GhciExtractor(this@ReplService)

			when (val result = extractor.extractIfNeeded { progress ->
				_state.value = ReplState.ExtractProgress(
					progress.current, progress.total, progress.message)
			}) {
				is ExtractionResult.Ready -> {
					ghciDir = result.rootDir
					startGHCi(result.rootDir)
				}
				is ExtractionResult.Failed -> {
					_state.value = ReplState.Error(result.reason)
				}
				is ExtractionResult.NeedsSpace -> {
					_state.value = ReplState.Error(
						"Need ${result.required / 1024 / 1024}MB, " +
						"have ${result.available / 1024 / 1024}MB free")
				}
			}
		}
	}

	private suspend fun startGHCi(rootDir: java.io.File) {
		_state.value = ReplState.Starting
		ghciDir = rootDir

		ghciProcess = GHCiProcess(this, rootDir)
		val result = ghciProcess!!.start()

		result.onFailure { e ->
			_state.value = ReplState.Error("Failed to start GHCi: ${e.message}")
			return
		}

		scope.launch {
			ghciProcess!!.output.collect { output ->
				_output.emit(output)
				when (output) {
					is ReplOutput.Raw -> {
						val parser = OutputParser()
						parser.isPromptLine(output.text)?.let { prompt ->
							_state.value = ReplState.Prompt(
								prompt.module, prompt.isContinuation)
						}
					}
					is ReplOutput.Error -> {
						_state.value = ReplState.Ready
					}
					else -> { }
				}
			}
		}

		delay(3000)
		if (_state.value is ReplState.Starting) {
			_state.value = ReplState.Ready
		}

		healthCheckJob = scope.launch {
			while (isActive) {
				delay(30_000)
				if (ghciProcess?.isAlive() != true) {
					_state.value = ReplState.Error("GHCi process died")
					ghciProcess?.shutdown()
					startGHCi(rootDir)
				}
			}
		}
	}

	private fun createNotification(text: String): Notification {
		val channelId = "haskell_repl"
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				channelId, "Haskell REPL",
				NotificationManager.IMPORTANCE_LOW)
			getSystemService(NotificationManager::class.java)
				.createNotificationChannel(channel)
		}
		return NotificationCompat.Builder(this, channelId)
			.setContentTitle("Haskell REPL")
			.setContentText(text)
			.setSmallIcon(android.R.drawable.ic_menu_edit)
			.setOngoing(true)
			.build()
	}

	companion object {
		const val NOTIFICATION_ID = 1001
	}
}
