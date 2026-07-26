package com.kino.puber.core.error

import com.kino.puber.R
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DefaultErrorHandlerTest {

    @Test
    fun map_usesLocalizedGenericCopyInsteadOfThrowableTransportDetails() {
        val transportDetails =
            "OAuth response decoding failed: https://api.example.test/oauth?access_token=secret"

        val error = DefaultErrorHandler(FakeResourceProvider())
            .map(IllegalStateException(transportDetails))

        assertEquals("string_${R.string.error_generic}", error.message)
        assertFalse(error.message.contains(transportDetails))
    }
}
