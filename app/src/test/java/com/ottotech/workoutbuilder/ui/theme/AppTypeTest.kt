package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import com.ottotech.workoutbuilder.ui.brand.BrandFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the one-pass font migration: no style may fall back to the system font. */
class AppTypeTest {
    private val styles: Map<String, TextStyle> = AppType::class.java.declaredMethods
        .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java }
        .associate { m -> m.name.removePrefix("get").replaceFirstChar { it.lowercase() } to m.invoke(AppType) as TextStyle }

    @Test
    fun `every style is on a brand font`() {
        val brand = setOf(BrandFonts.ArchivoExpanded, BrandFonts.SpaceGrotesk)
        assertTrue(styles.size >= 20, "Reflection found only ${styles.keys}")
        val off = styles.filterValues { it.fontFamily !in brand }.keys
        assertTrue(off.isEmpty(), "Not on a brand font: $off")
    }

    @Test
    fun `every style sets its own line height`() {
        val unset = styles.filterValues { !it.lineHeight.isSpecified }.keys
        assertTrue(unset.isEmpty(), "No explicit lineHeight: $unset")
    }

    @Test
    fun `archivo is only for titles and big numbers`() {
        val archivo = styles.filterValues { it.fontFamily == BrandFonts.ArchivoExpanded }.keys
        assertEquals(setOf("screenTitle", "timerDigits", "leadInDigits"), archivo)
    }
}
