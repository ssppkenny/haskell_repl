package com.example.haskellrepl.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {

	private val modelFile: File
		get() = File(context.filesDir, "models/ggml-tiny.en-q5_1.bin")

	val isModelReady: Boolean
		get() = modelFile.exists() && modelFile.length() > 10_000_000

	private val modelUrl =
		"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin"

	fun getModelPath(): String = modelFile.absolutePath

	suspend fun downloadIfNeeded(
		onProgress: suspend (Int) -> Unit = {}
	): Result<Unit> = withContext(Dispatchers.IO) {
		if (isModelReady) return@withContext Result.success(Unit)

		try {
			modelFile.parentFile?.mkdirs()
			val url = URL(modelUrl)
			val connection = url.openConnection() as HttpURLConnection
			connection.connectTimeout = 15_000
			connection.readTimeout = 120_000

			val totalSize = connection.contentLength
			var downloaded = 0L

			connection.inputStream.use { input ->
				modelFile.outputStream().use { output ->
					val buffer = ByteArray(8192)
					var bytesRead: Int
					while (input.read(buffer).also { bytesRead = it } != -1) {
						output.write(buffer, 0, bytesRead)
						downloaded += bytesRead
						if (totalSize > 0) {
							val pct = (downloaded * 100 / totalSize).toInt()
							onProgress(pct)
						}
					}
				}
			}
			Result.success(Unit)
		} catch (e: IOException) {
			modelFile.delete()
			Result.failure(e)
		}
	}
}
