package com.kino.puber.profile

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets

internal object BaselineNetworkBlocker {

    @Synchronized
    fun install(context: Context, allowedOrigin: String) {
        val normalizedOrigin = allowedOrigin.removeSuffix("/").lowercase()
        val current = installedSelector
        if (current?.allowedOrigin == normalizedOrigin) return

        val journal = BaselineNetworkJournal(context.applicationContext)
        val selector = BlockingProxySelector(normalizedOrigin, journal)
        ProxySelector.setDefault(selector)
        installedSelector = selector
    }

    private var installedSelector: BlockingProxySelector? = null

    private class BlockingProxySelector(
        val allowedOrigin: String,
        private val journal: BaselineNetworkJournal,
    ) : ProxySelector() {

        override fun select(uri: URI): List<Proxy> {
            requireNotNull(uri) { "URI must not be null" }
            return if (origin(uri) == allowedOrigin) {
                listOf(Proxy.NO_PROXY)
            } else {
                journal.record(uri)
                listOf(DENY_PROXY)
            }
        }

        override fun connectFailed(uri: URI, sa: SocketAddress?, ioe: java.io.IOException) {
            if (origin(uri) != allowedOrigin) {
                journal.record(uri)
            }
        }

        private fun origin(uri: URI): String {
            val scheme = uri.scheme.orEmpty().lowercase()
            val host = uri.host.orEmpty().lowercase()
            val port = uri.port.takeIf { it >= 0 } ?: defaultPort(scheme)
            return "$scheme://$host:$port"
        }

        private fun defaultPort(scheme: String): Int = when (scheme) {
            "http" -> HTTP_PORT
            "https" -> HTTPS_PORT
            else -> -1
        }
    }

    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
    private const val DENY_PROXY_PORT = 9
    private val DENY_PROXY = Proxy(
        Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved("127.0.0.1", DENY_PROXY_PORT),
    )
}

internal class BaselineNetworkJournal(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY)
    private val journalFile = File(directory, JOURNAL_FILE)
    private val lockFile = File(directory, LOCK_FILE)

    init {
        check(directory.isDirectory || directory.mkdirs()) {
            "Failed to create baseline network journal directory"
        }
    }

    fun record(uri: URI) = withFileLock {
        val violation = normalizedViolation(uri)
        val current = readEntries()
        if (violation !in current) {
            writeEntries((current + violation).takeLast(MAX_ENTRIES))
        }
    }

    fun snapshot(): List<String> = withFileLock(::readEntries)

    fun clear() = withFileLock {
        writeEntries(emptyList())
    }

    private fun readEntries(): List<String> {
        if (!journalFile.isFile) return emptyList()
        return journalFile.readLines(StandardCharsets.UTF_8)
            .filter(String::isNotBlank)
            .takeLast(MAX_ENTRIES)
    }

    private fun writeEntries(entries: List<String>) {
        val payload = entries.joinToString(ENTRY_SEPARATOR)
            .toByteArray(StandardCharsets.UTF_8)
        RandomAccessFile(journalFile, "rw").use { file ->
            file.setLength(0)
            file.write(payload)
            file.fd.sync()
        }
    }

    private fun normalizedViolation(uri: URI): String {
        val scheme = uri.scheme.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        val port = uri.port.takeIf { it >= 0 } ?: when (scheme) {
            "http" -> HTTP_PORT
            "https" -> HTTPS_PORT
            else -> -1
        }
        val path = uri.rawPath.orEmpty()
            .ifBlank { "/" }
            .replace('\n', '_')
            .replace('\r', '_')
        return "$scheme://$host:$port$path".take(MAX_ENTRY_LENGTH)
    }

    private inline fun <T> withFileLock(block: () -> T): T =
        synchronized(PROCESS_LOCK) {
            RandomAccessFile(lockFile, "rw").use { lockAccess ->
                lockAccess.channel.lock().use {
                    block()
                }
            }
        }

    private companion object {
        const val HTTP_PORT = 80
        const val HTTPS_PORT = 443
        const val DIRECTORY = "baseline_network_journal"
        const val JOURNAL_FILE = "violations"
        const val LOCK_FILE = "journal.lock"
        const val ENTRY_SEPARATOR = "\n"
        const val MAX_ENTRIES = 64
        const val MAX_ENTRY_LENGTH = 512

        val PROCESS_LOCK = Any()
    }
}
