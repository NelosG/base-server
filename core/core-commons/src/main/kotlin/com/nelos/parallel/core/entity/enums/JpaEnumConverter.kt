package com.nelos.parallel.core.entity.enums

import jakarta.persistence.AttributeConverter
import java.lang.reflect.ParameterizedType

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class JpaEnumConverter<T : JpaEnum> : AttributeConverter<T, String> {

    private val enumClass: Class<T> = run {
        val type = this.javaClass.genericSuperclass as ParameterizedType
        @Suppress("UNCHECKED_CAST")
        type.actualTypeArguments[0] as Class<T>
    }

    override fun convertToDatabaseColumn(attribute: T?): String? {
        return attribute?.getDbKey()
    }

    override fun convertToEntityAttribute(dbData: String?): T? {
        if (dbData == null) {
            return null
        }
        enumClass.enumConstants.forEach { prop ->
            if (prop.getDbKey() == dbData) {
                return prop
            }
        }
        //TODO: Add logs
        throw IllegalArgumentException(String.format("Invalid value '%s' for %s.", dbData, enumClass.simpleName))
    }
}