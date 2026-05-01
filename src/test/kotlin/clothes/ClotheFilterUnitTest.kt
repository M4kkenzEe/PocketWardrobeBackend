package com.example.clothes

import com.example.database.data.model.ClotheFilter
import com.example.database.data.model.RgbColor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClotheFilterUnitTest {

    @Test
    fun `isEmpty is true when all fields are null`() {
        assertTrue(ClotheFilter().isEmpty)
    }

    @Test
    fun `isEmpty is true when only colorTolerance is set but color is null`() {
        assertTrue(ClotheFilter(colorTolerance = 10.0).isEmpty)
    }

    @Test
    fun `isEmpty is false when categories is set`() {
        assertFalse(ClotheFilter(categories = listOf("tops")).isEmpty)
    }

    @Test
    fun `isEmpty is false when materials is set`() {
        assertFalse(ClotheFilter(materials = listOf("cotton")).isEmpty)
    }

    @Test
    fun `isEmpty is false when fits is set`() {
        assertFalse(ClotheFilter(fits = listOf("slim")).isEmpty)
    }

    @Test
    fun `isEmpty is false when seasons is set`() {
        assertFalse(ClotheFilter(seasons = listOf("summer")).isEmpty)
    }

    @Test
    fun `isEmpty is false when styles is set`() {
        assertFalse(ClotheFilter(styles = listOf("casual")).isEmpty)
    }

    @Test
    fun `isEmpty is false when brands is set`() {
        assertFalse(ClotheFilter(brands = listOf("Nike")).isEmpty)
    }

    @Test
    fun `isEmpty is false when color is set`() {
        assertFalse(ClotheFilter(color = RgbColor(255, 0, 0)).isEmpty)
    }

    @Test
    fun `isEmpty is false when searchQuery is set`() {
        assertFalse(ClotheFilter(searchQuery = "shirt").isEmpty)
    }

    // Edge case: route нормализует через takeIf { it.isNotEmpty() }, поэтому пустой список
    // никогда не попадает в ClotheFilter в продакшне, но isEmpty проверяет только null
    @Test
    fun `isEmpty is false when categories is empty list (documents null vs empty distinction)`() {
        assertFalse(ClotheFilter(categories = emptyList()).isEmpty)
    }

    @Test
    fun `isEmpty is false when multiple fields are non-null`() {
        assertFalse(ClotheFilter(
            categories = listOf("tops"),
            seasons = listOf("summer"),
            searchQuery = "shirt"
        ).isEmpty)
    }
}
