package io.snapplay.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val props: SnapPlayProperties,
    @param:Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private val jwksUri: String,
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = props.allowedOrigins.split(",").map { it.trim() }
        config.allowCredentials = true
        config.allowedMethods = listOf("*")
        config.allowedHeaders = listOf("*")
        config.exposedHeaders = listOf("X-Request-Id")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    @ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
    fun demoSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
    fun prodSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health", "/actuator/**")
                    .permitAll()
                    .requestMatchers("/r/**")
                    .permitAll()
                    .requestMatchers("/v1/partner/events")
                    .permitAll()
                    .requestMatchers("/v1/sessions/milestone")
                    .permitAll()
                if (jwksUri.isNotBlank()) {
                    auth.requestMatchers("/v1/**").authenticated()
                } else {
                    auth.requestMatchers("/v1/**").permitAll()
                }
                auth.anyRequest().permitAll()
            }
        if (jwksUri.isNotBlank()) {
            http.oauth2ResourceServer { oauth2 -> oauth2.jwt { } }
        }
        return http.build()
    }
}
