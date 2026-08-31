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

        FixtureId.entries.forEach { fixture ->
            assertTrue(
                "$fixture is missing or changed in Android assets",
                PlayerTestFixtures.verifySha256(fixture, context),
            )
        }
        assertTrue(
            "fixture checksum manifest is missing",
            PlayerTestFixtures.committedChecksums(context).isNotEmpty(),
        )
    }
}
