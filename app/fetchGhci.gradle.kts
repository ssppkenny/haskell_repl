import java.io.ByteArrayOutputStream

tasks.register("fetchGhci") {
	group = "ghci"
	description = "Download Termux GHC 9.12.2 ARM64 .deb + dependencies, extract, and place into jniLibs and assets"

	val assetsDir = layout.projectDirectory.dir("src/main/assets/ghci-root")
	val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
	val workDir = layout.buildDirectory.dir("tmp/ghci-bundle")
	val repoBase = "https://packages.termux.dev/apt/termux-main/pool/main"

	val packages = mapOf(
		"ghc" to "g/ghc/ghc_9.12.2-2_aarch64.deb",
		"libiconv" to "libi/libiconv/libiconv_1.18-1_aarch64.deb",
		"libffi" to "libf/libffi/libffi_3.5.2_aarch64.deb",
		"libgmp" to "libg/libgmp/libgmp_6.3.0-2_aarch64.deb",
		"ncurses" to "n/ncurses/ncurses_6.6.20260307+really6.5.20250830_aarch64.deb",
		"libandroid-posix-semaphore" to "liba/libandroid-posix-semaphore/libandroid-posix-semaphore_0.1-4_aarch64.deb"
	)

	doLast {
		workDir.get().asFile.mkdirs()
		assetsDir.asFile.mkdirs()
		jniLibsDir.asFile.mkdirs()

		packages.forEach { (pkgName, pkgPath) ->
			val debFile = workDir.get().file("${pkgName}.deb").asFile
			if (!debFile.exists()) {
				val url = "$repoBase/$pkgPath"
				logger.lifecycle("Downloading $pkgName from $url ...")
				try {
					java.net.URI(url).toURL().openStream().use { input ->
						debFile.outputStream().use { output -> input.copyTo(output) }
					}
				} catch (e: Exception) {
					logger.warn("Failed to download $pkgName: ${e.message}. Continuing without it.")
					return@forEach
				}
			}

			val extractDir = workDir.get().file("extracted-${pkgName}").asFile
			if (!extractDir.resolve("data").isDirectory) {
				logger.lifecycle("Extracting $pkgName ...")
				extractDir.mkdirs()
				runCmd(extractDir, "tar", "xf", debFile.absolutePath)
				val dataTarXz = extractDir.resolve("data.tar.xz")
				if (dataTarXz.exists()) {
					runCmd(extractDir, "tar", "xf", dataTarXz.absolutePath)
				} else {
					val dataTar = extractDir.listFiles()?.find { it.name.startsWith("data.tar") }
					if (dataTar != null) {
						runCmd(extractDir, "tar", "xf", dataTar.absolutePath)
					}
				}
			}

			val usrDir = extractDir.toPath()
				.resolve("data/data/com.termux/files/usr").toFile()
			if (!usrDir.isDirectory) {
				logger.warn("Termux prefix not found for $pkgName, skipping")
				return@forEach
			}

			val libDir = usrDir.resolve("lib")
			if (libDir.isDirectory) {
				libDir.listFiles()?.forEach { file ->
					val name = file.name
					if (name.endsWith(".so") || name.contains(".so.") || file.isDirectory) {
						if (file.isDirectory && name.startsWith("ghc-")) {
							if (pkgName == "ghc") {
								file.copyRecursively(assetsDir.file("lib/${name}").asFile, overwrite = true)
							}
						} else if (!file.isDirectory) {
							val destName = if (name.startsWith("lib")) name else "lib${name}"
							jniLibsDir.file(destName).asFile.let { dest ->
								file.copyTo(dest, overwrite = true)
							}
							jniLibsDir.file(name).asFile.let { dest ->
								file.copyTo(dest, overwrite = true)
							}
							assetsDir.file("extralibs/${name}").asFile.let { dest ->
								dest.parentFile.mkdirs()
								file.copyTo(dest, overwrite = true)
							}
						}
					}
				}
			}

			if (pkgName == "ghc") {
				val binDir = usrDir.resolve("bin")
				if (binDir.isDirectory) {
					binDir.listFiles()?.forEach { file ->
						file.copyTo(assetsDir.file("bin/${file.name}").asFile, overwrite = true)
					}
				}
				val includeDir = usrDir.resolve("include")
				if (includeDir.isDirectory) {
					includeDir.copyRecursively(assetsDir.file("include").asFile, overwrite = true)
				}
				val shareDir = usrDir.resolve("share")
				shareDir.listFiles()?.forEach { file ->
					if (file.isDirectory && file.name.startsWith("ghc-")) {
						file.copyRecursively(assetsDir.file("share/${file.name}").asFile, overwrite = true)
					}
				}
			}
		}

		logger.lifecycle("Stripping unused files...")
		val archDir = assetsDir.file("lib/ghc-9.12.2/lib/aarch64-linux-ghc-9.12.2-inplace").asFile
		if (archDir.isDirectory) {
			archDir.listFiles()?.forEach { pkgDir ->
				if (pkgDir.isDirectory) {
					pkgDir.listFiles()?.forEach { file ->
						if (file.name.endsWith(".a") || file.name.endsWith(".p_o") || file.name.endsWith(".p_hi")) {
							file.delete()
						}
					}
				}
			}
		}
		assetsDir.file("lib/ghc-9.12.2/lib/html").asFile.deleteRecursively()
		assetsDir.file("lib/ghc-9.12.2/lib/latex").asFile.deleteRecursively()
		assetsDir.asFile.resolve("lib/ghc-9.12.2/lib").listFiles()?.forEach { file ->
			if (file.name.endsWith(".js") || file.name.endsWith(".mjs")) {
				file.delete()
			}
		}

		assetsDir.file("VERSION").asFile.writeText("9.12.2-2")
		logger.lifecycle("Done. Assets ready in ${assetsDir.asFile.absolutePath}")
	}
}

fun runCmd(vararg args: String) {
	val proc = ProcessBuilder(args.toList())
		.redirectErrorStream(true)
		.start()
	val out = ByteArrayOutputStream()
	proc.inputStream.copyTo(out)
	val exitCode = proc.waitFor()
	if (exitCode != 0) {
		throw GradleException("Command failed (exit $exitCode): ${args.joinToString(" ")}\n${out}")
	}
}

fun runCmd(dir: java.io.File, vararg args: String) {
	val proc = ProcessBuilder(args.toList())
		.directory(dir)
		.redirectErrorStream(true)
		.start()
	val out = ByteArrayOutputStream()
	proc.inputStream.copyTo(out)
	val exitCode = proc.waitFor()
	if (exitCode != 0) {
		throw GradleException("Command failed (exit $exitCode): ${args.joinToString(" ")}\n${out}")
	}
}
