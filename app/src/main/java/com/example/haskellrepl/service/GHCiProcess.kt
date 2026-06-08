package com.example.haskellrepl.service

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.*

class GHCiProcess(
	private val context: Context,
	private val ghciRootDir: File
) {

	private var process: Process? = null
	private val ptyBridge = PtyBridge()
	private val parser = OutputParser()
	private val outputChannel = Channel<ReplOutput>(Channel.BUFFERED)
	private var outputStream: OutputStream? = null
	private var scope: CoroutineScope? = null
	private var outputJob: Job? = null

	val output: Flow<ReplOutput> = outputChannel.receiveAsFlow()

	fun start(): Result<Unit> = runCatching {
		val pty = ptyBridge.openPty()
		val slaveFile = File(pty.slavePath)

		val projectDir = File(ghciRootDir, "project")
		if (!projectDir.exists()) projectDir.mkdirs()

		val ghciBin = File(ghciRootDir, "lib/ghc-9.12.2/bin/ghc-9.12.2")
		if (!ghciBin.canExecute()) {
			return Result.failure(IllegalStateException(
				"ghc binary not found or not executable at ${ghciBin.absolutePath}"))
		}

		val extralibsDir = File(ghciRootDir, "extralibs")
		val ghcLibDir = File(ghciRootDir, "lib/ghc-9.12.2/lib")

		val startScript = File(ghciRootDir, "start-ghci.sh")
		val ghcLib = ghcLibDir.absolutePath
		val extraLibs = extralibsDir.absolutePath
		val ghcBin = ghciBin.absolutePath

		val settingsFile = File(ghcLibDir, "settings")
		if (!settingsFile.exists()) {
			return Result.failure(IllegalStateException("settings file not found"))
		}
		val origSettings = File(ghcLibDir, "settings.orig")
		if (!origSettings.exists()) {
			settingsFile.copyTo(origSettings, overwrite = false)
		}
		val patched = origSettings.readText()
			.replace("\"clang\"", "\"/system/bin/true\"")
			.replace("\"clang++\"", "\"/system/bin/true\"")
		settingsFile.writeText(patched)

		startScript.writeText("""
#!/system/bin/sh

GHCLIB=$ghcLib
export LD_LIBRARY_PATH=$extraLibs:${'$'}GHCLIB
cd ${'$'}GHCLIB

exec /system/bin/linker64 $ghcBin --interactive -fbyte-code -ignore-dot-ghci -B${'$'}GHCLIB "${'$'}@"
""".trimIndent())
		startScript.setExecutable(true)

		val env = ProcessBuilder()
			.directory(projectDir)
			.command(
				"/system/bin/sh",
				startScript.absolutePath
			)
			.redirectInput(ProcessBuilder.Redirect.from(slaveFile))
			.redirectOutput(ProcessBuilder.Redirect.to(slaveFile))
			.redirectError(ProcessBuilder.Redirect.to(slaveFile))
			.apply {
				environment().apply {
					put("HOME", projectDir.absolutePath)
					put("GHC_PACKAGE_PATH",
						"${ghcLibDir.absolutePath}/package.conf.d")
					put("TERM", "xterm-256color")
					put("LANG", "en_US.UTF-8")
				}
			}
			.start()

		process = env
		val masterFd = pty.masterFd
		outputStream = ptyBridge.fileOutputStream(masterFd)

		val inputStream = ptyBridge.fileInputStream(masterFd)
		val reader = BufferedReader(InputStreamReader(inputStream))

		scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
		outputJob = scope?.launch {
			val promptMarker = "GHCI_END_MARKER"
			try {
				reader.use { r ->
					val buffer = StringBuilder()
					while (isActive) {
						val ch = r.read()
						if (ch == -1) break
						buffer.append(ch.toChar())
						if (buffer.endsWith(promptMarker)) {
							val full = buffer.toString()
							buffer.clear()
							val text = full.removeSuffix(promptMarker)
							val clean = parser.stripAnsiCodes(text)
								.replace("\r\n", "\n")
								.replace("\r", "\n")
								.replace("^J", "")
							val lines = clean.split("\n").map { it.trim() }.filter { it.isNotBlank() }
							for (line in lines) {
								trySend(ReplOutput.Value(line))
							}
						}
					}
				}
			} catch (_: IOException) {
				trySend(ReplOutput.Error(
					"GHCi process disconnected", null, null))
			}
		}

		scope?.launch {
			delay(500)
			sendCommand(":set prompt \"GHCI_END_MARKER\"")
			sendCommand(":set prompt-cont \"\"")
		}
	}

	fun sendCommand(command: String) {
		try {
			outputStream?.write("$command\r\n".toByteArray())
			outputStream?.flush()
		} catch (e: IOException) {
			trySend(ReplOutput.Error(
				"Failed to send command: ${e.message}", null, null))
		}
	}

	fun interrupt() {
		val p = process ?: return
		try {
			val pidField = p.javaClass.getDeclaredField("pid")
			pidField.isAccessible = true
			val pid = pidField.getInt(p)
			Runtime.getRuntime().exec(arrayOf("kill", "-SIGINT", pid.toString()))
		} catch (_: Exception) {
			p.destroy()
		}
	}

	fun shutdown() {
		outputJob?.cancel()
		scope?.cancel()
		try {
			sendCommand(":quit")
			process?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
		} catch (_: Exception) { }
		process?.destroyForcibly()
		process = null
		try { outputStream?.close() } catch (_: Exception) { }
	}

	fun isAlive(): Boolean = process?.isAlive == true

	private fun trySend(output: ReplOutput) {
		outputChannel.trySend(output)
	}
}
