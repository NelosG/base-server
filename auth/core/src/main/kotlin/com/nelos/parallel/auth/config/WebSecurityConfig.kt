package com.nelos.parallel.auth.config

import com.nelos.parallel.auth.filter.CookieJwtAuthenticationFilter
import com.nelos.parallel.auth.filter.JwtAuthFilter
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.enums.UserType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.web.filter.CharacterEncodingFilter
import java.nio.charset.StandardCharsets


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig @Autowired constructor(
    private val userDetailsProviderService: UserDetailsProviderService,
) {

    @Autowired(required = false)
    private var authenticationProviders: MutableList<AuthenticationProvider> = mutableListOf()

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationProvider(passwordEncoder: PasswordEncoder): AuthenticationProvider {
        val authProvider = DaoAuthenticationProvider(userDetailsProviderService)
        authProvider.setPasswordEncoder(passwordEncoder)
        return authProvider
    }

    @Bean
    fun authManager(http: HttpSecurity): AuthenticationManager {
        val authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder::class.java)

        for (provider in authenticationProviders) {
            authenticationManagerBuilder.authenticationProvider(provider)
        }
        return authenticationManagerBuilder.build()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity, authenticationManager: AuthenticationManager): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            }
            .formLogin { formLogin ->
                formLogin.loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login?error=true")
                    .permitAll()
            }
            .logout { logout ->
                logout.logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", CookieJwtAuthenticationFilter.COOKIE_NAME)
                    .permitAll()
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/endpoint", "/error", "/login", "/login/**").permitAll()
                    .requestMatchers("/admin/**").hasRole(UserType.ADMIN.name)
                    .anyRequest().authenticated()
            }.exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { request, response, authException ->
                        // Только для случаев, когда пользователь не аутентифицирован
                        response.status = HttpStatus.UNAUTHORIZED.value()
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("""{"error": "Unauthorized", "message": "${authException.message}"}""")
                    }
                    .accessDeniedHandler { request, response, accessDeniedException ->
                        // Только для случаев, когда у пользователя нет прав
                        response.status = HttpStatus.FORBIDDEN.value()
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("""{"error": "Forbidden", "message": "${accessDeniedException.message}"}""")
                    }
            }.authenticationManager(authenticationManager)

        val encodingFilter = CharacterEncodingFilter()
        encodingFilter.encoding = StandardCharsets.UTF_8.name()
        encodingFilter.setForceEncoding(true)

        http.addFilterBefore(encodingFilter, CsrfFilter::class.java)
            .addFilterBefore(JwtAuthFilter(authenticationManager), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(
                CookieJwtAuthenticationFilter(authenticationManager),
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}