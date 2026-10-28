package com.yg.liquidink.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yg.liquidink.core.GlassIntensity
import com.yg.liquidink.core.ReaderBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPersistenceTest {

    @Test
    fun readerAppearanceSurvivesStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = SettingsStore(context)
        first.setReaderBackground(ReaderBackground.PARCHMENT)
        first.setGlassEffectsEnabled(false)
        first.setGlassIntensity(GlassIntensity.HIGH)
        first.setAnimationsEnabled(false)

        val second = SettingsStore(context)
        assertEquals(ReaderBackground.PARCHMENT, second.settings.value.readerBackground)
        assertFalse(second.settings.value.glassEffectsEnabled)
        assertEquals(GlassIntensity.HIGH, second.settings.value.glassIntensity)
        assertFalse(second.settings.value.animationsEnabled)
    }
}
