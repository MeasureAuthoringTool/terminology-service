package gov.cms.madie.terminology.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

  @Bean
  protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(withDefaults())
        .csrf(withDefaults())
        .authorizeHttpRequests(
            request ->
                request.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
        .sessionManagement(
            sessionMgt -> sessionMgt.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer((oauth2) -> oauth2.jwt(withDefaults()))
        .headers(
            headers ->
                headers
                    .xssProtection(xss -> xss.headerValue(HeaderValue.ENABLED_MODE_BLOCK))
                    .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' .....")));

    return http.build();
  }
}
