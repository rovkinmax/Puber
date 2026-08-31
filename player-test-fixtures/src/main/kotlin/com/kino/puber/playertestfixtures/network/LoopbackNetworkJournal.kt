package com.kino.puber.playertestfixtures.network

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.charset.StandardCharsets

class LoopbackNetworkJournal(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY)
    private val journalFile = File(directory, JOURNAL_FILE)
    private val lockFile = File(directory, LOCK_FILE)

    init {
        check(directory.isDirectory || directory.mkdirs()) {
            "Failed to create loopback network journal directory"
        }
    }

    fun record(uri: URI) = withFileLock {
        val violation = normalize(uri)
        val current = readEntries()
        if (violation !in current) writeEntries((current + violation).takeLast(MAX_ENTRIES))
    }

    fun snapshot(): List<String> = withFileLock(::readEntries)

    fun clear() = withFileLock { writeEntries(emptyList()) }

    private fun readEntries(): List<String> {
        if (!journalFile.isFile) return emptyList()
        return journalFile.readLines(StandardCharsets.UTF_8)
            .filter(String::isNotBlank)
            .takeLast(MAX_ENTRIES)
    }

    private fun writeEntries(entries: List<String>) {
        val payload = entries.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
        RandomAccessFile(journalFile, "rw").use { file ->
            file.setLength(0)
            file.write(payload)
            file.fd.sync()
        }
    }

    private fun normalize(uri: URI): String {
        val scheme = uri.scheme.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        val port = uri.port.takeIf { it >= 0 } ?: when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
            .replace('\n', '_')
            .replace('\r', '_')
        return "$scheme://$host:$port$path".take(MAX_ENTRY_LENGTH)
    }

    private inline fun <T> withFileLock(block: () -> T): T =
        synchronized(PROCESS_LOCK) {
            RandomAccessFile(lockFile, "rw").use { access ->
                access.channel.lock().use { block() }
            }
        }

    private companion object {
        const val DIRECTORY = "player_network_journal"
        const val JOURNAL_FILE = "violations"
        const val LOCK_FILE = "journal.lock"
        const val MAX_ENTRIES = 64
        const val MAX_ENTRY_LENGTH = 512
        val PROCESS_LOCK = Any()
    }
}
