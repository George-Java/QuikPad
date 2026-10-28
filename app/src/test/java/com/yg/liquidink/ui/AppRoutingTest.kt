package com.yg.liquidink.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutingTest {

    @Test
    fun settingsHasPriorityOverAnOpenDocument() {
        assertEquals(
            AppSurface.SETTINGS,
            resolveAppSurface(settingsOpen = true, hasEditor = true),
        )
    }

    @Test
    fun settingsAlsoReplacesHome() {
        assertEquals(
            AppSurface.SETTINGS,
            resolveAppSurface(settingsOpen = true, hasEditor = false),
        )
    }

    @Test
    fun editorIsUsedWhenSettingsAreClosed() {
        assertEquals(
            AppSurface.EDITOR,
            resolveAppSurface(settingsOpen = false, hasEditor = true),
        )
    }

    @Test
    fun homeIsTheFallback() {
        assertEquals(
            AppSurface.HOME,
            resolveAppSurface(settingsOpen = false, hasEditor = false),
        )
    }
}
