package com.example.haskellrepl.learning

import com.example.haskellrepl.service.ReplOutput

class ErrorExplainer {

	data class Explanation(val short: String, val detail: String)

	private val patterns = mapOf(
		Regex("Couldn't match type.*with actual type") to Explanation(
			"Type mismatch",
			"You're using a value of one type where a different type is expected."
		),
		Regex("Variable not in scope") to Explanation(
			"Unknown name",
			"This name hasn't been defined. Check for typos or define it first."
		),
		Regex("Not in scope: data constructor") to Explanation(
			"Unknown constructor",
			"This data constructor isn't available. Check the module imports."
		),
		Regex("No instance for.*arising from") to Explanation(
			"Missing typeclass instance",
			"The type you're using doesn't implement the required typeclass."
		),
		Regex("parse error.*possibly incorrect indentation") to Explanation(
			"Indentation error",
			"Haskell uses indentation to define blocks. Check your spacing."
		),
		Regex("Ambiguous occurrence") to Explanation(
			"Name collision",
			"This name is defined in multiple imported modules. Use a qualified import."
		),
		Regex("Prelude.(\\w+).*expecting.*IO") to Explanation(
			"Missing IO context",
			"This function requires an IO context (do block or main)."
		),
		Regex("The type signature for.*lacks an accompanying binding") to Explanation(
			"Missing definition",
			"You declared a type signature but didn't provide a definition."
		),
		Regex("Occurs check") to Explanation(
			"Infinite type",
			"Your expression would require an infinite type. Check for self-referential types."
		),
		Regex("Not in scope:.*module") to Explanation(
			"Missing module",
			"This module hasn't been loaded. Use :load or :m to import it."
		)
	)

	fun explain(error: ReplOutput.Error): Explanation {
		for ((pattern, explanation) in patterns) {
			if (pattern.containsMatchIn(error.message)) {
				return explanation
			}
		}
		return Explanation("Unexpected error", error.message)
	}
}
