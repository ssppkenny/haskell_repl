package com.example.haskellrepl.extract

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class ExtractionProgress(val current: Int, val total: Int, val message: String)
sealed class ExtractionResult {
	data class Ready(val rootDir: File) : ExtractionResult()
	data class Failed(val reason: String) : ExtractionResult()
	data class NeedsSpace(val required: Long, val available: Long) : ExtractionResult()
}

class GhciExtractor(private val context: Context) {

	private val rootDir: File
		get() = File(context.filesDir, "ghci-root")

	private val sentinelFile: File
		get() = File(rootDir, "EXTRACTION_COMPLETE")

	val isExtracted: Boolean
		get() = sentinelFile.exists()
			&& rootDir.resolve("bin/ghci").canExecute()
			&& rootDir.resolve("extralibs").isDirectory

	private val assetsPrefix = "ghci-root"

	suspend fun extractIfNeeded(
		onProgress: suspend (ExtractionProgress) -> Unit = {}
	): ExtractionResult = withContext(Dispatchers.IO) {
		if (isExtracted) return@withContext ExtractionResult.Ready(rootDir)

		val requiredSpace = estimateAssetSize()
		val statFs = StatFs(context.filesDir.absolutePath)
		val availableSpace = statFs.availableBytes
		if (availableSpace < requiredSpace) {
			return@withContext ExtractionResult.NeedsSpace(requiredSpace, availableSpace)
		}

		try {
			if (rootDir.exists()) rootDir.deleteRecursively()
			rootDir.mkdirs()
			extractAssets(rootDir, assetsPrefix, onProgress)
			sentinelFile.createNewFile()
			ExtractionResult.Ready(rootDir)
		} catch (e: IOException) {
			ExtractionResult.Failed("Extraction failed: ${e.message}")
		}
	}

	private fun estimateAssetSize(): Long {
		var total = 0L
		collectAssetPaths(assetsPrefix) { path ->
			try {
				val fd = context.assets.openFd(path)
				total += fd.length
				fd.close()
			} catch (_: Exception) { }
		}
		return total * 2
	}

	private fun extractAssets(
		targetDir: File,
		assetPath: String,
		onProgress: suspend (ExtractionProgress) -> Unit
	) {
		val assetManager = context.assets
		val entries = assetManager.list(assetPath) ?: return

		for (entry in entries) {
			val fullAssetPath = if (assetPath.isEmpty()) entry else "$assetPath/$entry"
			val childEntries = assetManager.list(fullAssetPath)
			if (childEntries != null && childEntries.isNotEmpty()) {
				val childDir = File(targetDir, entry)
				childDir.mkdirs()
				extractAssets(childDir, fullAssetPath, onProgress)
			} else {
				val outFile = File(targetDir, entry)
				outFile.parentFile?.mkdirs()
				assetManager.open(fullAssetPath).use { input ->
					outFile.outputStream().use { output ->
						input.copyTo(output)
					}
				}
				outFile.setExecutable(true, false)
			}
		}
	}

	private fun collectAssetPaths(path: String, onFile: (String) -> Unit) {
		val entries = context.assets.list(path) ?: return
		for (entry in entries) {
			val full = if (path.isEmpty()) entry else "$path/$entry"
			if (context.assets.list(full)?.isNotEmpty() == true) {
				collectAssetPaths(full, onFile)
			} else {
				onFile(full)
			}
		}
	}
}
