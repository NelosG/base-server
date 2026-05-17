package com.nelos.parallel.commons.entity.enums

import jakarta.persistence.Converter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Generic test for [JpaEnumConverter] using a tiny test enum. Catches the
 * common breakages: forgetting [JpaEnum.getDbKey], swapping keys, or relying
 * on `name()` instead of the explicit db key.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class JpaEnumConverterTest {

    private enum class Color(private val dbKey: String) : JpaEnum {
        RED("R"),
        GREEN("G"),
        BLUE("B");

        override fun getDbKey(): String = dbKey
    }

    @Converter
    private class ColorJpaConverter : JpaEnumConverter<Color>()

    private val converter = ColorJpaConverter()

    @Test
    fun `enum to db key uses the explicit dbKey not the constant name`() {
        assertEquals("R", converter.convertToDatabaseColumn(Color.RED))
        assertEquals("G", converter.convertToDatabaseColumn(Color.GREEN))
        assertEquals("B", converter.convertToDatabaseColumn(Color.BLUE))
    }

    @Test
    fun `null entity attribute serialises to null`() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun `null db column deserialises to null`() {
        assertNull(converter.convertToEntityAttribute(null))
    }

    @Test
    fun `known db key deserialises back to the matching enum constant`() {
        assertEquals(Color.RED, converter.convertToEntityAttribute("R"))
        assertEquals(Color.GREEN, converter.convertToEntityAttribute("G"))
        assertEquals(Color.BLUE, converter.convertToEntityAttribute("B"))
    }

    @Test
    fun `unknown db key throws with the offending value in the message`() {
        val ex = assertThrows<IllegalArgumentException> { converter.convertToEntityAttribute("X") }
        assertEquals(true, ex.message?.contains("'X'"))
        assertEquals(true, ex.message?.contains("Color"))
    }

    @Test
    fun `db keys are matched case-sensitively`() {
        // 'r' is NOT a registered key - only 'R' is. Lowercase must NOT silently match.
        assertThrows<IllegalArgumentException> { converter.convertToEntityAttribute("r") }
    }
}
