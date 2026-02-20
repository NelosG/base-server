package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType

/**
 * Information about a transport channel on a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = TransportInfo.Deserializer::class)
class TransportInfo(
    val type: TransportType,
    val status: AdapterStatus? = null,
    val config: TransportConfig? = null,
) {

    class Deserializer : JsonDeserializer<TransportInfo>() {

        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TransportInfo {
            val mapper = p.codec as ObjectMapper
            val node: JsonNode = mapper.readTree(p)

            val type = TransportType.fromValue(
                node.get("type")?.asText() ?: error("TransportInfo: missing 'type' field")
            )
            val status = node.get("status")?.asText()?.let { AdapterStatus.fromValue(it) }

            val configNode = node.get("config")
            val config = if (configNode != null && !configNode.isNull) {
                when (type) {
                    TransportType.HTTP -> mapper.treeToValue(configNode, TransportConfig.HttpConfig::class.java)
                    TransportType.AMQP -> mapper.treeToValue(configNode, TransportConfig.AmqpConfig::class.java)
                }
            } else null

            return TransportInfo(type, status, config)
        }
    }
}
