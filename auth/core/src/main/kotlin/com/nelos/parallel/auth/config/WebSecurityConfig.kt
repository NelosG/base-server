package com.nelos.parallel.auth.config

import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.filter.CookieJwtAuthenticationFilter
import com.nelos.parallel.auth.filter.JwtAuthFilter
import com.nelos.parallel.auth.service.UserDetailsProviderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
 * Spring Security configuration: defines the filter chain, authentication providers, and access rules.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig @Autowired constructor(
    private val userDetailsProviderService: UserDetailsProviderService,
) {

    @Autowired(required = false)
    private var authenticationProviders: List<AuthenticationProvider> = emptyList()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(passwordEncoder: PasswordEncoder): AuthenticationProvider {
        return DaoAuthenticationProvider(userDetailsProviderService).apply {
            setPasswordEncoder(passwordEncoder)
        }
    }

    @Bean
    fun authManager(http: HttpSecurity): AuthenticationManager {
        val builder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
        authenticationProviders.forEach { builder.authenticationProvider(it) }
        return builder.build()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity, authenticationManager: AuthenticationManager): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .logout { logout ->
                logout.logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", CookieJwtAuthenticationFilter.COOKIE_NAME)
                    .permitAll()
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/login", "/register").permitAll()
                    .requestMatchers("/api/view/**").permitAll()
                    .requestMatchers("/api/register", "/api/callback/**").permitAll()
                    .requestMatchers("/endpoint", "/error", "/error-page").permitAll()
                    .requestMatchers("/webjars/**", "/css/**", "/js/**", "/images/**").permitAll()
                    .requestMatchers("/admin/**").hasRole(UserType.ADMIN.name)
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    response.sendRedirect("/login?unauthorized=true")
                }
            }
            .authenticationManager(authenticationManager)

        val encodingFilter = CharacterEncodingFilter().apply {
            encoding = StandardCharsets.UTF_8.name()
            setForceEncoding(true)
        }

        http.addFilterBefore(encodingFilter, CsrfFilter::class.java)
            .addFilterBefore(JwtAuthFilter(authenticationManager), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(
                CookieJwtAuthenticationFilter(authenticationManager),
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
