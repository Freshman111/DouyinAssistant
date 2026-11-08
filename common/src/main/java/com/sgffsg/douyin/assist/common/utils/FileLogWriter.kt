package com.sgffsg.douyin.assist.common.utils

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

internal class FileLogWriter(private val resolveDirectory: () -> File) {
    @Volatile
    var isEnabled = false
        private set

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FileLogWriter").apply { isDaemon = true }
    }
    private val slots = Semaphore(512)
    private val droppedEntries = AtomicInteger()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT)

    // The writer and file size are owned by the executor thread.
    private var directory: File? = null
    private var stream: FileOutputStream? = null
    private var sizeBytes = 0L
    private var writeFailed = false

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (!enabled) executor.execute {
            closeStream()
            directory = null
            writeFailed = false
        }
    }

    @Synchronized
    fun append(level: Char, tag: String, message: String, error: Throwable?) {
        if (!isEnabled) return
        if (!slots.tryAcquire()) {
            droppedEntries.incrementAndGet()
            return
        }
        val timestamp = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val text = message.take(MAX_TEXT_LENGTH)
        executor.execute {
            try {
                if (!writeFailed) {
                    val time = dateFormat.format(Date(timestamp))
                    val dropped = droppedEntries.getAndSet(0)
                    if (dropped > 0) write("$time W/$TAG dropped_entries=$dropped\n")
                    val stackTrace = error?.let { Log.getStackTraceString(it).take(MAX_TEXT_LENGTH) }.orEmpty()
                    write("$time $level/$tag [$threadName] $text\n$stackTrace")
                }
            } catch (e: IOException) {
                fail(e)
            } catch (e: SecurityException) {
                fail(e)
            } finally {
                slots.release()
            }
        }
    }

    private fun write(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val directory = directory ?: resolveDirectory().also { this.directory = it }
        val active = logFile(directory, 0)
        if (stream == null) {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create log directory: $directory")
            }
            sizeBytes = active.length()
        }
        if (sizeBytes + bytes.size > MAX_FILE_BYTES) {
            closeStream()
            rotate(directory)
            sizeBytes = 0
        }
        val output = stream ?: FileOutputStream(active, true).also { stream = it }
        output.write(bytes)
        sizeBytes += bytes.size
    }

    private fun rotate(directory: File) {
        for (index in MAX_FILES - 1 downTo 1) {
            val previous = logFile(directory, index - 1)
            if (!previous.exists()) continue
            val next = logFile(directory, index)
            if ((next.exists() && !next.delete()) || !previous.renameTo(next)) {
                throw IOException("Cannot rotate log file: $previous")
            }
        }
    }

    private fun logFile(directory: File, index: Int): File =
        File(directory, if (index == 0) "app.log" else "app.$index.log")

    private fun fail(error: Exception) {
        writeFailed = true
        closeStream()
        Log.w(TAG, "Unable to save application logs", error)
    }

    private fun closeStream() {
        try {
            stream?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Unable to close application log", e)
        } finally {
            stream = null
        }
    }

    private companion object {
        const val TAG = "FileLogWriter"
        const val MAX_FILES = 3
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_TEXT_LENGTH = 32_768
    }
}
