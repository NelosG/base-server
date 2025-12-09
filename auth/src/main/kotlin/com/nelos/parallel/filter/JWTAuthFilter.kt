package com.nelos.parallel.filter

import com.nelos.parallel.auth.JWTTokenProvider
import com.nelos.parallel.exceptions.UserNotFoundxception
import com.nelos.parallel.service.UserService
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component
class JWTAuthFilter @Autowired constructor(
    private val tokenService: JWTTokenProvider,
    private val userService: UserService,
) : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain
    ) {
        val requestTokenHeader = request.getHeader("Authorization") //Extract the Authorization Header:


        //this token is actually concatinated with Bearer space("Bearer ")
        //Check for a Bearer Token:
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }


        //Ensure that the token in the header is correctly formatted as "Bearer <your-jwt-token>".
        val token = requestTokenHeader.split("Bearer ".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[1] // Extract JWT token from the header
        val login = tokenService.validateToken(token) //generate the token


        //Check if  the Security Context is Empty
        if (SecurityContextHolder.getContext().authentication == null) {
            //Retrieve the User Entity

            val user = userService.findByLogin(login) ?: throw UserNotFoundxception("User not found for login $login")


            //Create an Authentication Token
            val authenticationToken = UsernamePasswordAuthenticationToken(
                user, null, user.authorities
            )


            //Set Authentication Details
            authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)
            //Set Authentication in Security Context
            SecurityContextHolder.getContext().authentication = authenticationToken
        }
        //Continue the Filter Chain
        filterChain.doFilter(request, response)
    }
}