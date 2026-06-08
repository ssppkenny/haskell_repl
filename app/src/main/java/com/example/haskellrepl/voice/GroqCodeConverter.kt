package com.example.haskellrepl.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GroqCodeConverter(private val apiKey: String) {

	private val endpoint = "https://api.groq.com/openai/v1/chat/completions"
	private val model = "llama-3.3-70b-versatile"

	private val systemPrompt = """
You are a speech-to-code converter. The user spoke into a microphone and a speech-to-text engine
transcribed it. Convert the transcription into valid Haskell code for a GHCi REPL.

Common transcription errors to fix:
- "if" at the start of a line (without then/else) usually means the letter "f"
- "the play", "the ply", "a play", "a ply" usually mean function application (just space)
- "equals" or "is" means "="
- "plus" means "+", "minus" means "-", "times" means "*"
- "arrow" or "goes to" means "->"
- Single letters spoken as words ("be" = b, "see" = c, "dee" = d)
- Spoken operators should become their Haskell symbols

Output ONLY the Haskell code. No explanation, no markdown, no backticks.
""".trimIndent()

	suspend fun convert(speech: String): String? = withContext(Dispatchers.IO) {
		try {
			val body = JSONObject().apply {
				put("model", model)
				put("messages", JSONArray().apply {
					put(JSONObject().apply {
						put("role", "system")
						put("content", systemPrompt)
					})
					put(JSONObject().apply {
						put("role", "user")
						put("content", speech)
					})
				})
				put("temperature", 0.0)
				put("max_tokens", 256)
			}

			val conn = URL(endpoint).openConnection() as HttpURLConnection
			conn.requestMethod = "POST"
			conn.doOutput = true
			conn.setRequestProperty("Authorization", "Bearer $apiKey")
			conn.setRequestProperty("Content-Type", "application/json")
			conn.connectTimeout = 10_000
			conn.readTimeout = 15_000

			conn.outputStream.use { os ->
				os.write(body.toString().toByteArray())
			}

			if (conn.responseCode != 200) {
				conn.disconnect()
				return@withContext null
			}

			val response = conn.inputStream.bufferedReader().readText()
			conn.disconnect()

			val json = JSONObject(response)
			val content = json
				.getJSONArray("choices")
				.getJSONObject(0)
				.getJSONObject("message")
				.getString("content")
				.trim()

			if (content.isBlank()) null else content
		} catch (_: Exception) {
			null
		}
	}
}
