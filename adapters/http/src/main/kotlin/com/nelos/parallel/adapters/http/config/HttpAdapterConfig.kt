package com.nelos.parallel.adapters.http.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Spring bean wiring for the HTTP adapter.
 *
 * The RestClient used by [com.nelos.parallel.adapters.http.impl.HttpNodeAdapterImpl]
 * has explicit connect and read timeouts so a hung or unreachable test-runner
 * node cannot block a Tomcat worker thread forever. The JDK's default
 * `HttpClient` (and therefore Spring's `RestClient.create()`) has no timeouts
 * - a single dead node was enough to saturate the request pool under burst.
 *
 * Timeout choices:
 * - `connectTimeout = 5s` - node either accepts the TCP connection quickly or
 *   it's effectively dead.
 * - `readTimeout = 60s` - covers `submitTask` (the slowest call: engine queues
 *   the job and synchronously responds with acceptance ack). All other calls
 *   are sub-second under normal conditions.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
class HttpAdapterConfig {

    @Bean("prl.nodeRestClient")
    fun nodeRestClient(): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(60))
        }
        return RestClient.builder().requestFactory(factory).build()
    }
}
