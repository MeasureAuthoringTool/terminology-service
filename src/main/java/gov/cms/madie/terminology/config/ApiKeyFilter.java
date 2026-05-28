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

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  private static final RequestMatcher PROTECTED_MATCHER =
      new OrRequestMatcher(
          Arrays.asList(
              PathPatternRequestMatcher.withDefaults().matcher("/terminology/ValueSet"),
              PathPatternRequestMatcher.withDefaults().matcher("/terminology/CodeSystem")));

  private final String expectedApiKey;
  private final String headerName;

  public ApiKeyFilter(
      @Value("${madie.api-key}") String expectedApiKey,
      @Value("${madie.api-key-header}") String headerName) {
    this.expectedApiKey = expectedApiKey;
    this.headerName = headerName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!PROTECTED_MATCHER.matches(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String provided = request.getHeader(headerName);
    if (provided == null || provided.trim().isEmpty() || !provided.equals(expectedApiKey)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
      response
          .getOutputStream()
          .write("Invalid or missing API key".getBytes(StandardCharsets.UTF_8));
      return;
    }

    filterChain.doFilter(request, response);
  }
}
