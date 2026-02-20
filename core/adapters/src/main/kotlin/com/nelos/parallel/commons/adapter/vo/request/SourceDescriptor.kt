package com.nelos.parallel.commons.adapter.vo.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * Sealed class representing a source descriptor for task submission.
 * Deserialized by checking the presence of a "url" field to distinguish
 * between [GitSource] and [LocalSource] without a discriminator field.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonDeserialize(using = SourceDescriptor.Deserializer::class)
sealed class SourceDescriptor {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class GitSource @JsonCreator constructor(
        @param:JsonProperty("url") val url: String,
        @param:JsonProperty("branch") val branch: String? = null,
        @param:JsonProperty("token") val token: String? = null,
    ) : SourceDescriptor()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class LocalSource @JsonCreator constructor(
        @param:JsonProperty("path") val path: String,
    ) : SourceDescriptor()

    class Deserializer : JsonDeserializer<SourceDescriptor>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SourceDescriptor {
            val mapper = p.codec as ObjectMapper
            val node: JsonNode = mapper.readTree(p)
            return when {
                node.has("url") -> mapper.treeToValue(node, GitSource::class.java)
                node.has("path") -> mapper.treeToValue(node, LocalSource::class.java)
                else -> throw com.fasterxml.jackson.databind.exc.MismatchedInputException.from(
                    p, SourceDescriptor::class.java,
                    "SourceDescriptor must have either 'url' (GitSource) or 'path' (LocalSource)"
                )
            }
        }
    }
}
