package com.example.haskellrepl.learning

data class TutorialSnippet(
	val title: String,
	val expression: String,
	val category: String
)

object TutorialSnippets {

	val all = listOf(
		TutorialSnippet("Basic arithmetic", "1 + 2 * 3", "Basics"),
		TutorialSnippet("Type of a function", ":type map", "Types"),
		TutorialSnippet("List comprehension", "[x*2 | x <- [1..10], x `mod` 2 == 0]", "Lists"),
		TutorialSnippet("Map over list", "map (*2) [1..5]", "Lists"),
		TutorialSnippet("Filter list", "filter even [1..10]", "Lists"),
		TutorialSnippet("Fold left", "foldl (+) 0 [1..10]", "Lists"),
		TutorialSnippet("Lambda expression", "(\\x -> x * x) 5", "Functions"),
		TutorialSnippet("Function composition", "(map (*2) . filter even) [1..10]", "Functions"),
		TutorialSnippet("Currying", "let add x y = x + y in add 3 4", "Functions"),
		TutorialSnippet("Define a function", "let double x = x * 2", "Definitions"),
		TutorialSnippet("Pattern matching", "let factorial 0 = 1; factorial n = n * factorial (n - 1)", "Definitions"),
		TutorialSnippet("Maybe type", ":info Maybe", "Types"),
		TutorialSnippet("List operations", ":browse Prelude", "Exploration"),
	)

	val byCategory: Map<String, List<TutorialSnippet>> = all.groupBy { it.category }
}
