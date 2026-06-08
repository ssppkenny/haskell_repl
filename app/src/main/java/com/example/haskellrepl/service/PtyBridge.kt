package com.example.haskellrepl.service

import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class PtyBridge {

	data class PtyResult(
		val masterFd: FileDescriptor,
		val slavePath: String
	)

	private external fun nativeOpenPty(): PtyResult
	private external fun nativeSetWinSize(masterFd: Int, rows: Int, cols: Int)

	companion object {
		init {
			System.loadLibrary("pty_bridge")
		}
	}

	fun openPty(): PtyResult = nativeOpenPty()

	fun setWindowSize(fd: FileDescriptor, rows: Int, cols: Int) {
		val fdInt = getFdInt(fd)
		nativeSetWinSize(fdInt, rows, cols)
	}

	fun fileInputStream(fd: FileDescriptor): FileInputStream =
		FileInputStream(fd)

	fun fileOutputStream(fd: FileDescriptor): FileOutputStream =
		FileOutputStream(fd)

	fun parcelFileDescriptor(fd: FileDescriptor): ParcelFileDescriptor =
		ParcelFileDescriptor.dup(fd)

	private fun getFdInt(fd: FileDescriptor): Int {
		val field = FileDescriptor::class.java.getDeclaredField("descriptor")
		field.isAccessible = true
		return field.getInt(fd)
	}
}
