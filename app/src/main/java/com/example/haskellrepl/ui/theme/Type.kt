package com.example.haskellrepl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val ReplTypography = Typography(
	bodyLarge = TextStyle(
		fontFamily = FontFamily.Monospace,
		fontSize = 14.sp,
		lineHeight = 20.sp
	),
	bodyMedium = TextStyle(
		fontFamily = FontFamily.Monospace,
		fontSize = 13.sp,
		lineHeight = 18.sp
	),
	bodySmall = TextStyle(
		fontFamily = FontFamily.Monospace,
		fontSize = 12.sp,
		lineHeight = 16.sp
	),
	labelSmall = TextStyle(
		fontFamily = FontFamily.Default,
		fontSize = 11.sp,
		lineHeight = 14.sp
	)
)
