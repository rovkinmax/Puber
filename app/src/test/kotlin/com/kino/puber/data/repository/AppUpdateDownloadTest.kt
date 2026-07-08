package com.kino.puber.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AppUpdateDownloadTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun parseSha256_returnsHash_whenChecksumUsesShasumFormat() {
        val result = AppUpdateChecksum.parseSha256("$HELLO_SHA256  puber-v1.5.0.apk\n")

        assertEquals(HELLO_SHA256, result)
    }

    @Test
    fun parseSha256_returnsHash_whenChecksumContainsRawHash() {
        val result = AppUpdateChecksum.parseSha256(HELLO_SHA256.uppercase())

        assertEquals(HELLO_SHA256, result)
    }

    @Test
    fun parseSha256_returnsNull_whenChecksumDoesNotContainSha256() {
        val result = AppUpdateChecksum.parseSha256("not-a-checksum  puber.apk")

        assertNull(result)
    }

    @Test
    fun verifySha256_returnsMatch_whenFileMatchesChecksum() {
        val file = File(tempDir, "puber.apk").apply {
            writeText("hello")
        }

        val result = AppUpdateChecksum.verifySha256(file, "$HELLO_SHA256  puber.apk")

        assertEquals(AppUpdateChecksumVerification.Match, result)
    }

    @Test
    fun verifySha256_returnsMismatch_whenFileDoesNotMatchChecksum() {
        val file = File(tempDir, "puber.apk").apply {
            writeText("different")
        }

        val result = AppUpdateChecksum.verifySha256(file, "$HELLO_SHA256  puber.apk")

        assertTrue(result is AppUpdateChecksumVerification.Mismatch)
        assertEquals(HELLO_SHA256, (result as AppUpdateChecksumVerification.Mismatch).expected)
    }

    @Test
    fun verifySha256_returnsInvalidChecksum_whenChecksumCannotBeParsed() {
        val file = File(tempDir, "puber.apk").apply {
            writeText("hello")
        }

        val result = AppUpdateChecksum.verifySha256(file, "invalid")

        assertEquals(AppUpdateChecksumVerification.InvalidChecksum, result)
    }

    private companion object {
        const val HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }
}
