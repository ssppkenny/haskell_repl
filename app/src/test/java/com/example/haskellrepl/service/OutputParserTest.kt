package com.example.haskellrepl.service

import org.junit.Assert.*
import org.junit.Test

class OutputParserTest {

	private val parser = OutputParser()

	@Test
	fun `parse value with prompt marker`() {
		val results = parser.parse("2\n\u0004GHCI_PROMPT\u0004")
		assertEquals(1, results.size)
		assertTrue(results[0] is ReplOutput.Value)
		assertEquals("2", (results[0] as ReplOutput.Value).text)
	}

	@Test
	fun `parse error output`() {
		val input = "<interactive>:3:7: error: Variable not in scope: xyz"
		val results = parser.parse("$input\n\u0004GHCI_PROMPT\u0004")
		assertEquals(1, results.size)
		assertTrue(results[0] is ReplOutput.Error)
		val err = results[0] as ReplOutput.Error
		assertEquals(3, err.line)
		assertEquals(7, err.col)
		assertTrue(err.message.contains("Variable not in scope"))
	}

	@Test
	fun `parse type info`() {
		val result = parser.parseTypeOutput("length :: Foldable t => t a -> Int")
		assertNotNull(result)
		assertEquals("length", result?.expression)
		assertTrue(result?.typeSig?.contains("Foldable") == true)
	}

	@Test
	fun `detect normal prompt`() {
		val prompt = parser.isPromptLine("Prelude> ")
		assertNotNull(prompt)
		assertEquals("Prelude", prompt?.module)
		assertFalse(prompt?.isContinuation ?: true)
	}

	@Test
	fun `detect continuation prompt`() {
		val prompt = parser.isPromptLine("Prelude| ")
		assertNotNull(prompt)
		assertTrue(prompt?.isContinuation ?: false)
	}

	@Test
	fun `strip ANSI codes`() {
		val result = parser.stripAnsiCodes("\u001B[31mred\u001B[0m text")
		assertEquals("red text", result)
	}

	@Test
	fun `empty raw input returns empty`() {
		val results = parser.parse("")
		assertTrue(results.isEmpty())
	}
}
