package gov.cms.madie.terminology.config;

import gov.cms.madie.terminology.clients.UserRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] AUTH_WHITELIST = {
    "/actuator/**", "/terminology/ValueSet", "/terminology/CodeSystem"
  };

  @Bean
  protected SecurityFilterChain filterChain(
      HttpSecurity http, UserRoleConverter roleConverter, ApiKeyFilter apiKeyFilter)
      throws Exception {
    http.cors(withDefaults())
        .csrf(withDefaults())
        .authorizeHttpRequests(
            request ->
                request
                    .requestMatchers(AUTH_WHITELIST)
                    .permitAll()
                    .requestMatchers("/terminology/admin/**")
                    .hasRole("MADIE-ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterAfter(apiKeyFilter, HeaderWriterFilter.class)
        .sessionManagement(
            sessionMgt -> sessionMgt.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oAuth2ResourceServerConfigurer ->
                oAuth2ResourceServerConfigurer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
        .headers(
            headers ->
                headers
                    .xssProtection(xss -> xss.headerValue(HeaderValue.ENABLED_MODE_BLOCK))
                    .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' .....")));

    return http.build();
  }
}
