package com.kino.puber.playertestfixtures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerTestFixturesAndroidTest {

    @Test
    fun packagedAndroidAsset_isTheSameCommittedFixture() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val checksums = PlayerTestFixtures.committedChecksums(context)

        assertTrue("fixture checksum manifest is missing", checksums.isNotEmpty())
        checksums.forEach { (path, expected) ->
            assertTrue(
                "$path is missing or changed in packaged Android assets",
                PlayerTestFixtures.verifySha256(path, expected, context),
            )
        }
    }
}
