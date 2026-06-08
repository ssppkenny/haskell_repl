package com.example.haskellrepl.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
	primary = TerminalGreen,
	secondary = TerminalBlue,
	tertiary = TerminalPurple,
	background = TerminalBackground,
	surface = TerminalSurface,
	surfaceVariant = TerminalSurfaceVariant,
	onPrimary = TerminalBackground,
	onSecondary = TerminalBackground,
	onTertiary = TerminalBackground,
	onBackground = TerminalWhite,
	onSurface = TerminalWhite,
	onSurfaceVariant = TerminalGray,
	error = TerminalRed,
	onError = TerminalBackground
)

@Composable
fun HaskellReplTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = DarkColorScheme,
		typography = ReplTypography,
		content = content
	)
}
