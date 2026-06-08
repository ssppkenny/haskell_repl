package com.example.haskellrepl.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haskellrepl.ui.theme.*
import com.example.haskellrepl.voice.VoiceInputManager

data class ReplLine(
	val text: String,
	val type: ReplLineType,
	val id: Long = counter++
) {
	companion object {
		private var counter = 0L
	}
}

enum class ReplLineType { INPUT, VALUE, ERROR, INFO, PROMPT }

@Composable
fun ReplScreen(
	outputLines: List<ReplLine>,
	currentPrompt: String,
	isContinuation: Boolean,
	isReady: Boolean,
	onSendExpression: (String) -> Unit,
	onInterrupt: () -> Unit,
	voiceState: VoiceInputManager.State,
	onMicPressStart: () -> Unit,
	onMicPressEnd: () -> Unit,
	historyEnabled: Boolean,
	quickActionsEnabled: Boolean,
	groqKey: String?,
	onSetGroqKey: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	var inputValue by remember { mutableStateOf(TextFieldValue("")) }
	val listState = rememberLazyListState()
	var showKeyDialog by remember { mutableStateOf(false) }

	LaunchedEffect(voiceState) {
		if (voiceState is VoiceInputManager.State.Result) {
			val newText = voiceState.text
			inputValue = TextFieldValue(
				if (inputValue.text.isEmpty()) newText
				else inputValue.text + " " + newText
			)
		}
	}

	LaunchedEffect(outputLines.size) {
		if (outputLines.isNotEmpty()) {
			listState.animateScrollToItem(outputLines.size - 1)
		}
	}

	Row(modifier = modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.weight(if (historyEnabled || quickActionsEnabled) 0.7f else 1f)
				.fillMaxHeight()
				.statusBarsPadding()
		) {
			OutputArea(
				lines = outputLines,
				listState = listState,
				modifier = Modifier.weight(1f)
			)

			InputArea(
				value = inputValue,
				onValueChange = { inputValue = it },
				prompt = currentPrompt,
				isContinuation = isContinuation,
				enabled = isReady,
				onSend = {
					val text = inputValue.text
					if (text.isNotBlank()) {
						onSendExpression(text)
						inputValue = TextFieldValue("")
					}
				},
				onInterrupt = onInterrupt,
				voiceState = voiceState,
				onMicPressStart = onMicPressStart,
				onMicPressEnd = onMicPressEnd,
				onSettingsClick = { showKeyDialog = true }
			)
		}

		if (historyEnabled || quickActionsEnabled) {
			SidePanel(
				modifier = Modifier
					.weight(0.3f)
					.fillMaxHeight(),
				quickActionsEnabled = quickActionsEnabled,
				onQuickAction = { action ->
					inputValue = TextFieldValue("$action ${inputValue.text}")
				}
			)
		}
	}

	if (showKeyDialog) {
		GroqKeyDialog(
			currentKey = groqKey,
			onDismiss = { showKeyDialog = false },
			onSave = {
				onSetGroqKey(it)
				showKeyDialog = false
			}
		)
	}
}

@Composable
private fun OutputArea(
	lines: List<ReplLine>,
	listState: androidx.compose.foundation.lazy.LazyListState,
	modifier: Modifier = Modifier
) {
	LazyColumn(
		state = listState,
		modifier = modifier
			.fillMaxWidth()
			.background(TerminalBackground)
			.padding(8.dp),
		verticalArrangement = Arrangement.spacedBy(2.dp)
	) {
		items(lines, key = { it.id }) { line ->
			OutputLine(line)
		}
	}
}

@Composable
private fun OutputLine(line: ReplLine) {
	val color = when (line.type) {
		ReplLineType.INPUT -> PromptColor
		ReplLineType.VALUE -> TerminalGreen
		ReplLineType.ERROR -> TerminalRed
		ReplLineType.INFO -> TerminalGray
		ReplLineType.PROMPT -> PromptColor
	}

	Text(
		text = line.text,
		style = MaterialTheme.typography.bodyMedium.copy(
			fontFamily = FontFamily.Monospace,
			color = color
		),
		modifier = Modifier.fillMaxWidth()
	)
}

@Composable
private fun InputArea(
	value: TextFieldValue,
	onValueChange: (TextFieldValue) -> Unit,
	prompt: String,
	isContinuation: Boolean,
	enabled: Boolean,
	onSend: () -> Unit,
	onInterrupt: () -> Unit,
	voiceState: VoiceInputManager.State,
	onMicPressStart: () -> Unit,
	onMicPressEnd: () -> Unit,
	onSettingsClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	Surface(
		modifier = modifier.fillMaxWidth().navigationBarsPadding(),
		color = TerminalSurface,
		tonalElevation = 4.dp
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			val promptChar = if (isContinuation) "|" else ">"
			Text(
				text = "$prompt$promptChar ",
				style = MaterialTheme.typography.bodyMedium.copy(
					fontFamily = FontFamily.Monospace,
					color = PromptColor
				)
			)

			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				enabled = enabled,
				modifier = Modifier.weight(1f),
				textStyle = TextStyle(
					fontFamily = FontFamily.Monospace,
					fontSize = 14.sp,
					color = TerminalWhite
				),
				singleLine = !isContinuation,
				decorationBox = { innerTextField ->
					if (value.text.isEmpty()) {
						Text(
							text = if (enabled) "enter expression..." else "loading...",
							style = MaterialTheme.typography.bodyMedium.copy(
								fontFamily = FontFamily.Monospace,
								color = TerminalGray
							)
						)
					}
					innerTextField()
				}
			)

			if (enabled) {
				MicButton(
					state = voiceState,
					onPressStart = onMicPressStart,
					onPressEnd = onMicPressEnd
				)
				IconButton(onClick = onSettingsClick) {
					Text("\u2699", fontSize = 16.sp)
				}
				IconButton(onClick = onSend) {
					Text(">", color = TerminalGreen, fontSize = 18.sp)
				}
			}
		}
	}
}

@Composable
private fun SidePanel(
	modifier: Modifier = Modifier,
	quickActionsEnabled: Boolean,
	onQuickAction: (String) -> Unit
) {
	Column(
		modifier = modifier
			.background(TerminalSurface)
			.padding(8.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		if (quickActionsEnabled) {
			Text("Quick Actions",
				style = MaterialTheme.typography.labelSmall,
				color = TerminalGray)
			QuickActionChip(":type", onQuickAction)
			QuickActionChip(":info", onQuickAction)
			QuickActionChip(":kind", onQuickAction)
			QuickActionChip(":browse", onQuickAction)
		}
	}
}

@Composable
private fun QuickActionChip(label: String, onClick: (String) -> Unit) {
	Surface(
		onClick = { onClick(label) },
		shape = RoundedCornerShape(4.dp),
		color = TerminalSurfaceVariant,
		modifier = Modifier.fillMaxWidth()
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodySmall.copy(
				fontFamily = FontFamily.Monospace,
				color = TerminalBlue
			),
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
		)
	}
}

@Composable
private fun MicButton(
	state: VoiceInputManager.State,
	onPressStart: () -> Unit,
	onPressEnd: () -> Unit
) {
	var pressed by remember { mutableStateOf(false) }

	val modifier = Modifier
		.pointerInput(Unit) {
			detectTapGestures(
				onPress = {
					pressed = true
					onPressStart()
					tryAwaitRelease()
					pressed = false
					onPressEnd()
				}
			)
		}

	Box(modifier = modifier.padding(end = 4.dp)) {
		when (state) {
			is VoiceInputManager.State.Recording -> {
				Text("🔴", fontSize = 16.sp)
			}
			is VoiceInputManager.State.Transcribing -> {
				CircularProgressIndicator(
					modifier = Modifier.size(18.dp),
					color = TerminalBlue,
					strokeWidth = 2.dp
				)
			}
			is VoiceInputManager.State.Downloading -> {
				Text("⬇", fontSize = 16.sp)
			}
			is VoiceInputManager.State.Thinking -> {
				Text("🤖", fontSize = 16.sp)
			}
			is VoiceInputManager.State.Error -> {
				Text("⚠", fontSize = 16.sp, color = TerminalRed)
			}
			else -> {
				Text("🎤", fontSize = 16.sp)
			}
		}
	}
}

@Composable
private fun GroqKeyDialog(
	currentKey: String?,
	onDismiss: () -> Unit,
	onSave: (String) -> Unit
) {
	var keyValue by remember { mutableStateOf(currentKey ?: "") }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Groq API Key") },
		text = {
			Column {
				Text(
					text = "Enter your Groq API key for smart speech-to-code conversion. Get one at console.groq.com",
					style = MaterialTheme.typography.bodySmall,
					color = TerminalGray
				)
				Spacer(Modifier.height(8.dp))
				OutlinedTextField(
					value = keyValue,
					onValueChange = { keyValue = it },
					label = { Text("API Key") },
					visualTransformation = PasswordVisualTransformation(),
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
			}
		},
		confirmButton = {
			TextButton(onClick = { onSave(keyValue.trim()) }) {
				Text("Save")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}
