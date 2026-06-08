package com.example.haskellrepl.service

sealed class ReplOutput {
	data class Value(val text: String) : ReplOutput()
	data class Error(val message: String, val line: Int?, val col: Int?) : ReplOutput()
	data class TypeInfo(val expression: String, val typeSig: String) : ReplOutput()
	data class Prompt(val module: String, val isContinuation: Boolean) : ReplOutput()
	data class Raw(val text: String) : ReplOutput()
}

class OutputParser {

	private val valueRegex = Regex("GHCI_END_MARKER")

	private val errorRegex = Regex(
		"""<interactive>:(\d+):(\d+):\s*(?:warning:)?\s*(?:error:)?\s*(.+)""",
		RegexOption.DOT_MATCHES_ALL
	)

	private val typeInfoRegex = Regex("""(.+?)\s*::\s*(.+)""")

	fun parse(raw: String): List<ReplOutput> {
		val results = mutableListOf<ReplOutput>()

		if (valueRegex.containsMatchIn(raw)) {
			val parts = raw.split(valueRegex)
			for (part in parts) {
				val trimmed = part.trim()
				if (trimmed.isEmpty()) continue

				val errorMatch = errorRegex.find(trimmed)
				if (errorMatch != null) {
					val (line, col, msg) = errorMatch.destructured
					results.add(ReplOutput.Error(
						msg.trim(),
						line.toIntOrNull(),
						col.toIntOrNull()
					))
					continue
				}

				results.add(ReplOutput.Value(trimmed))
			}
		} else if (raw.isNotBlank()) {
			results.add(ReplOutput.Raw(raw))
		}

		return results
	}

	fun parseRawOutput(text: String): List<ReplOutput> {
		if (text.isBlank()) return emptyList()
		return text.trim().lines().map { line ->
			val clean = line.trim()
			val errorMatch = errorRegex.find(clean)
			if (errorMatch != null) {
				val (l, c, msg) = errorMatch.destructured
				ReplOutput.Error(msg.trim(), l.toIntOrNull(), c.toIntOrNull())
			} else {
				ReplOutput.Value(clean)
			}
		}
	}

	fun parseTypeOutput(raw: String): ReplOutput.TypeInfo? {
		val trimmed = raw.trim()
		val match = typeInfoRegex.find(trimmed) ?: return null
		val (expr, sig) = match.destructured
		val cleanExpr = expr.trim().removePrefix("\"").removeSuffix("\"")
		return ReplOutput.TypeInfo(cleanExpr, sig.trim())
	}

	fun isPromptLine(line: String): ReplOutput.Prompt? {
		val trimmed = line.trimEnd()
		val ghciPrompt = Regex("""^(\*?\w+(?:\.\w+)*)>\s*$""")
		val ghciCont = Regex("""^(\*?\w+(?:\.\w+)*)\|\s*$""")

		ghciPrompt.find(trimmed)?.let {
			return ReplOutput.Prompt(it.groupValues[1], false)
		}
		ghciCont.find(trimmed)?.let {
			return ReplOutput.Prompt(it.groupValues[1], true)
		}
		return null
	}

	fun stripAnsiCodes(text: String): String {
		return text
			.replace(Regex("\u001B\\[[\\d;]*[a-zA-Z]"), "")
			.replace("\r", "")
	}
}
