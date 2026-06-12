package gov.cms.madie.terminology.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Hapi Validators use a Basic Authentication and encoding. This class is to account for that and
 * use a service to service api key
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  private static final RequestMatcher PROTECTED_MATCHER =
      new OrRequestMatcher(
          Arrays.asList(
              PathPatternRequestMatcher.withDefaults().matcher("/terminology/ValueSet"),
              PathPatternRequestMatcher.withDefaults().matcher("/terminology/CodeSystem")));

  private final String expectedApiKey;

  public ApiKeyFilter(@Value("${madie.api-key}") String expectedApiKey) {
    this.expectedApiKey = expectedApiKey;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!PROTECTED_MATCHER.matches(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      sendUnauthorized(response);
      return;
    }

    try {
      // Extract and decode the Base64 credentials
      String base64Credentials = authHeader.substring("Basic ".length());
      String credentials =
          new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);

      // credentials format is "username:password"
      String[] parts = credentials.split(":", 2);
      if (parts.length != 2) {
        sendUnauthorized(response);
        return;
      }

      String password = parts[1];

      if (!password.equals(expectedApiKey)) {
        sendUnauthorized(response);
        return;
      }

      filterChain.doFilter(request, response);
    } catch (IllegalArgumentException e) {
      // Base64 decoding failed
      sendUnauthorized(response);
    }
  }

  private void sendUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"API\"");
    response
        .getOutputStream()
        .write("Invalid or missing credentials".getBytes(StandardCharsets.UTF_8));
  }
}
