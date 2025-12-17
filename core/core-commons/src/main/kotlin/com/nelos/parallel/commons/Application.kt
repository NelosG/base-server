package com.nelos.parallel.commons

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ImportResource
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@SpringBootApplication(scanBasePackages = ["com.nelos.parallel"])
@EnableTransactionManagement
@ImportResource("classpath*:config/*.xml")
class Application {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(Application::class.java, *args)
        }
    }
}
