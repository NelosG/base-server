package com.nelos.parallel.commons.adapter.entity

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

private val MAPPER = ObjectMapper()

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Converter
class CapabilitiesJsonConverter : AttributeConverter<Map<String, Any?>, String> {

    override fun convertToDatabaseColumn(attribute: Map<String, Any?>?): String? =
        attribute?.let { MAPPER.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): Map<String, Any?>? =
        dbData?.takeIf { it.isNotBlank() }?.let {
            @Suppress("UNCHECKED_CAST")
            MAPPER.readValue(it, Map::class.java) as Map<String, Any?>
        }
}

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Converter
class TransportsJsonConverter : AttributeConverter<List<TransportInfo>, String> {

    override fun convertToDatabaseColumn(attribute: List<TransportInfo>?): String? =
        attribute?.let { MAPPER.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): List<TransportInfo>? =
        dbData?.takeIf { it.isNotBlank() }?.let {
            val type = MAPPER.typeFactory.constructCollectionType(List::class.java, TransportInfo::class.java)
            MAPPER.readValue(it, type)
        }
}

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Converter
class ResourceProvidersJsonConverter : AttributeConverter<List<ResourceProviderInfo>, String> {

    override fun convertToDatabaseColumn(attribute: List<ResourceProviderInfo>?): String? =
        attribute?.let { MAPPER.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): List<ResourceProviderInfo>? =
        dbData?.takeIf { it.isNotBlank() }?.let {
            val type = MAPPER.typeFactory.constructCollectionType(List::class.java, ResourceProviderInfo::class.java)
            MAPPER.readValue(it, type)
        }
}
