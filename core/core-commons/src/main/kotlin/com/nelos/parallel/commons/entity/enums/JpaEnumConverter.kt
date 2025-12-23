package com.nelos.parallel.commons.entity.enums

import jakarta.persistence.AttributeConverter
import java.lang.reflect.ParameterizedType

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Suppress("ConverterNotAnnotatedInspection")
abstract class JpaEnumConverter<T : JpaEnum> : AttributeConverter<T, String> {

    @Suppress("UNCHECKED_CAST")
    private val enumClass: Class<T> =
        (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>

    override fun convertToDatabaseColumn(attribute: T?): String? {
        return attribute?.getDbKey()
    }

    override fun convertToEntityAttribute(dbData: String?): T? {
        return dbData?.let { key ->
            enumClass.enumConstants.firstOrNull { it.getDbKey() == key }
                ?: throw IllegalArgumentException("Invalid value '$key' for ${enumClass.simpleName}.")
        }
    }
}