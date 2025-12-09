package com.nelos.parallel.config

import com.nelos.parallel.filter.JWTAuthFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig @Autowired constructor(
    private val jwtAuthFilter: JWTAuthFilter,
) {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/endpoint", "/error", "/auth/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/books").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }.exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { request, response, authException ->
                        // Только для случаев, когда пользователь не аутентифицирован
                        response.status = HttpStatus.UNAUTHORIZED.value()
                        response.contentType = "application/json"
                        response.writer.write("""{"error": "Unauthorized", "message": "Authentication required"}""")
                    }
                    .accessDeniedHandler { request, response, accessDeniedException ->
                        // Только для случаев, когда у пользователя нет прав
                        response.status = HttpStatus.FORBIDDEN.value()
                        response.contentType = "application/json"
                        response.writer.write("""{"error": "Forbidden", "message": "Access denied"}""")
                    }
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    @Throws(Exception::class)
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}