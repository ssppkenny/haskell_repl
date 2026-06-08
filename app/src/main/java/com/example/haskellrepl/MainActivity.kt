package com.example.haskellrepl

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.haskellrepl.service.ReplService
import com.example.haskellrepl.service.ReplOutput
import com.example.haskellrepl.ui.*
import com.example.haskellrepl.ui.theme.*
import com.example.haskellrepl.voice.VoiceInputManager
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

	private var replService: ReplService? = null
	private val outputLines = MutableStateFlow<List<ReplLine>>(emptyList())
	private val stateFlow = MutableStateFlow<ReplService.ReplState>(ReplService.ReplState.Extracting)
	private val serviceFlag = mutableIntStateOf(0)
	private lateinit var voiceManager: VoiceInputManager
	private val groqKeyState = mutableStateOf<String?>(null)

	private val connection = object : ServiceConnection {
		override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
			replService = (service as ReplService.LocalBinder).getService()
			serviceFlag.intValue++
		}
		override fun onServiceDisconnected(name: ComponentName?) {
			replService = null
		}
	}

	private val micPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		if (granted) { /* ready */ }
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		voiceManager = VoiceInputManager(this)
		groqKeyState.value = voiceManager.getGroqApiKey()

		bindService(
			Intent(this, ReplService::class.java),
			connection,
			Context.BIND_AUTO_CREATE
		)
		startService(Intent(this, ReplService::class.java))

		setContent {
			HaskellReplTheme {
				val svcReady = serviceFlag.intValue
				val svc = if (svcReady > 0) replService else null

				LaunchedEffect(svcReady) {
					if (svc != null) {
						svc.state.collect { s -> stateFlow.value = s }
					}
				}
				LaunchedEffect(svcReady) {
					if (svc != null) {
						svc.output.collect { output ->
						val newLines = outputLines.value.toMutableList()
						when (output) {
							is ReplOutput.Raw -> {
								newLines.add(ReplLine(output.text, ReplLineType.INFO))
							}
							is ReplOutput.Value -> {
								newLines.add(ReplLine(output.text, ReplLineType.VALUE))
							}
							is ReplOutput.Error -> {
								newLines.add(ReplLine("Error: ${output.message}", ReplLineType.ERROR))
							}
							else -> {}
						}
						if (newLines.size > 200) newLines.removeAt(0)
						outputLines.value = newLines
					}
				}
			}

			val state by stateFlow.collectAsState()
			val voiceState = voiceManager.state.collectAsStateWithLifecycle().value
				val currentPrompt = when (state) {
					is ReplService.ReplState.Prompt -> (state as ReplService.ReplState.Prompt).module
					else -> "Prelude"
				}
				val isContinuation = (state as? ReplService.ReplState.Prompt)?.isContinuation ?: false
				val isReady = state is ReplService.ReplState.Ready || state is ReplService.ReplState.Prompt

				Surface(modifier = Modifier.fillMaxSize(), color = TerminalBackground) {
					when (state) {
						is ReplService.ReplState.Extracting,
						is ReplService.ReplState.ExtractProgress -> {
							LoadingScreen(
								message = if (state is ReplService.ReplState.ExtractProgress)
									(state as ReplService.ReplState.ExtractProgress).message
								else "Preparing Haskell environment..."
							)
						}
						is ReplService.ReplState.Starting -> {
							LoadingScreen(message = "Starting GHCi...")
						}
						is ReplService.ReplState.Error -> {
							ErrorScreen(
								message = (state as ReplService.ReplState.Error).message,
								onRetry = { replService?.restart() }
							)
						}
						else -> {
							val lines by outputLines.collectAsState()
							ReplScreen(
								outputLines = lines,
								currentPrompt = currentPrompt,
								isContinuation = isContinuation,
								isReady = isReady,
								onSendExpression = { expr ->
									replService?.sendExpression(expr)
								},
								onInterrupt = { replService?.interrupt() },
								voiceState = voiceState,
								onMicPressStart = {
									if (voiceManager.hasPermission()) {
										voiceManager.startRecording()
									} else {
										micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
									}
								},
								onMicPressEnd = {
									voiceManager.stopRecording()
								},
								historyEnabled = false,
								quickActionsEnabled = false,
								groqKey = groqKeyState.value,
								onSetGroqKey = { key ->
									voiceManager.setGroqApiKey(key)
									groqKeyState.value = key
								}
							)
						}
					}
				}
			}
		}
	}

	override fun onDestroy() {
		unbindService(connection)
		voiceManager.destroy()
		super.onDestroy()
	}
}

@Composable
private fun LoadingScreen(message: String) {
	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.fillMaxSize()
			.background(TerminalBackground)
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			CircularProgressIndicator(color = TerminalGreen)
			Spacer(Modifier.height(16.dp))
			Text(
				text = message,
				style = MaterialTheme.typography.bodyMedium.copy(
					fontFamily = FontFamily.Monospace,
					color = TerminalGray
				)
			)
		}
	}
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.fillMaxSize()
			.background(TerminalBackground)
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(
				text = message,
				style = MaterialTheme.typography.bodyMedium.copy(
					fontFamily = FontFamily.Monospace,
					color = TerminalRed
				),
				modifier = Modifier.padding(16.dp)
			)
			Spacer(Modifier.height(16.dp))
			Button(onClick = onRetry) {
				Text("Retry")
			}
		}
	}
}
