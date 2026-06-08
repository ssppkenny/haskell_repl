package com.example.haskellrepl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haskellrepl.ui.theme.*

@Composable
fun HaskellKeyboard(
	onKeyPress: (String) -> Unit,
	onEnter: () -> Unit,
	onBackspace: () -> Unit,
	modifier: Modifier = Modifier
) {
	var shiftActive by remember { mutableStateOf(false) }
	val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
	val rowH = if (landscape) 30.dp else 42.dp
	val gap = if (landscape) 2.dp else 3.dp
	val fontSize = if (landscape) 13.sp else 15.sp
	val specialFontSize = if (landscape) 12.sp else 14.sp

	Surface(
		modifier = modifier.fillMaxWidth(),
		color = TerminalSurface,
		tonalElevation = 8.dp
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 2.dp, vertical = if (landscape) 2.dp else 3.dp),
			verticalArrangement = Arrangement.spacedBy(gap)
		) {
			HkRow(Modifier.fillMaxWidth().height(rowH)) {
				for (c in "1234567890".toList()) HkKey( "$c", "$c", fontSize = fontSize) {
					onKeyPress("$c"); shiftActive = false
				}
			}
			HkRow(Modifier.fillMaxWidth().height(rowH)) {
				for (c in "!@#$%^&*()[]".toList()) HkKey( "$c", "$c", fontSize = fontSize) {
					onKeyPress("$c"); shiftActive = false
				}
			}
			HkRow(Modifier.fillMaxWidth().height(rowH)) {
				for (c in "qwertyuiop".toList()) {
					val ch = "$c"
					val ins = if (shiftActive) ch.uppercase() else ch
					val lbl = if (shiftActive) ch.uppercase() else ch
					HkKey( lbl, ins, fontSize = fontSize) { onKeyPress(ins); shiftActive = false }
				}
			}
			HkRow(Modifier.fillMaxWidth().height(rowH)) {
				for (c in "asdfghjkl;'".toList()) {
					val ch = "$c"
					val ins = if (shiftActive && ch[0].isLetter()) ch.uppercase() else ch
					val lbl = if (shiftActive && ch[0].isLetter()) ch.uppercase() else ch
					HkKey( lbl, ins, fontSize = fontSize) { onKeyPress(ins); shiftActive = false }
				}
			}
			HkRow(Modifier.fillMaxWidth().height(rowH)) {
				HkKey(
					label = "\u21E7",
					insert = "",
					weight = 1.2f,
					special = true,
					highlighted = shiftActive,
					fontSize = specialFontSize
				) { shiftActive = !shiftActive }
				for (c in "zxvbnm,./_".toList()) {
					val ch = "$c"
					val ins = if (shiftActive && ch[0].isLetter()) ch.uppercase() else ch
					val lbl = if (shiftActive && ch[0].isLetter()) ch.uppercase() else ch
					HkKey( lbl, ins, fontSize = fontSize) { onKeyPress(ins); shiftActive = false }
				}
			}
			HkRow(Modifier.fillMaxWidth().height(rowH)) {
				for ((lbl, ins) in listOf(
					"{" to "{", "}" to "}", ":" to ":",
					"\"" to "\"", "\\" to "\\", "|" to "|"
				)) {
					HkKey(lbl, ins, fontSize = fontSize) { onKeyPress(ins); shiftActive = false }
				}
				HkKey( "=", "=", fontSize = fontSize) { onKeyPress("="); shiftActive = false }
				HkKey( "+", "+", fontSize = fontSize) { onKeyPress("+"); shiftActive = false }
				HkKey( "-", "-", fontSize = fontSize) { onKeyPress("-"); shiftActive = false }
				HkKey( "<", "<", fontSize = fontSize) { onKeyPress("<"); shiftActive = false }
				HkKey( ">", ">", fontSize = fontSize) { onKeyPress(">"); shiftActive = false }
				HkKey( "->", "->", weight = 1.3f, fontSize = fontSize) { onKeyPress("->"); shiftActive = false }
				HkKey( "::", "::", weight = 1.3f, fontSize = fontSize) { onKeyPress("::"); shiftActive = false }
				HkKey( "\$", "\$", fontSize = fontSize) { onKeyPress("\$"); shiftActive = false }
				HkKey( ".", ".", fontSize = fontSize) { onKeyPress("."); shiftActive = false }
				HkKey( "", "", weight = 2.5f, special = true, fontSize = specialFontSize) { onKeyPress(" ") }
				HkKey(
					label = "\u21B5",
					insert = "",
					weight = 1.4f,
					special = true,
					fontSize = specialFontSize
				) { onEnter(); shiftActive = false }
				HkKey(
					label = "\u232B",
					insert = "",
					weight = 1.2f,
					special = true,
					fontSize = specialFontSize
				) { onBackspace(); shiftActive = false }
			}
		}
	}
}

@Composable
private fun HkRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(3.dp),
		content = content
	)
}

@Composable
private fun RowScope.HkKey(
	label: String,
	insert: String,
	weight: Float = 1f,
	special: Boolean = false,
	highlighted: Boolean = false,
	fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
	onClick: () -> Unit
) {
	val interactionSource = remember { MutableInteractionSource() }
	val pressed = interactionSource.collectIsPressedAsState().value

	val bg = when {
		highlighted -> TerminalBlue.copy(alpha = 0.4f)
		pressed -> TerminalSurfaceVariant
		special -> TerminalSurfaceVariant.copy(alpha = 0.7f)
		else -> TerminalSurface
	}

	Box(
		modifier = Modifier
			.weight(weight)
			.fillMaxHeight()
			.clip(RoundedCornerShape(5.dp))
			.background(bg)
			.border(0.5.dp, TerminalGray.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
			.clickable(interactionSource = interactionSource, indication = null) { onClick() },
		contentAlignment = Alignment.Center
	) {
		Text(
			text = label,
			style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
				fontFamily = FontFamily.Monospace,
				fontWeight = if (special) FontWeight.Bold else FontWeight.Normal,
				fontSize = fontSize,
				color = when {
					highlighted -> TerminalGreen
					special -> TerminalGray
					else -> TerminalWhite
				}
			)
		)
	}
}
