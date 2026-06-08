package gov.cms.madie.terminology.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

  @Test
  void allowsRequestWithValidBasicAuth() throws Exception {
    String expectedApiKey = "valid-key";
    ApiKeyFilter filter = new ApiKeyFilter(expectedApiKey);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain filterChain = mock(FilterChain.class);
    String credentials = "user:" + expectedApiKey;
    String base64 =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + base64);
    when(request.getRequestURI()).thenReturn("/terminology/ValueSet");
    when(request.getServletPath()).thenReturn("/terminology/ValueSet");
    when(request.getAttribute("org.springframework.web.util.ServletRequestPathUtils.PATH"))
        .thenReturn(null);
    when(request.getHttpServletMapping()).thenReturn(mock(HttpServletMapping.class));
    when(request.getHttpServletMapping().getMatchValue()).thenReturn("/terminology/ValueSet");
    filter.doFilterInternal(request, response, filterChain);
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void rejectsWhenAuthorizationHeaderMissing() throws Exception {
    String expectedApiKey = "valid-key";
    ApiKeyFilter filter = new ApiKeyFilter(expectedApiKey);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain filterChain = mock(FilterChain.class);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ServletOutputStream servletOutputStream =
        new ServletOutputStream() {
          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(WriteListener writeListener) {}

          @Override
          public void write(int b) throws IOException {
            outputStream.write(b);
          }
        };
    when(request.getRequestURI()).thenReturn("/terminology/ValueSet");
    when(request.getServletPath()).thenReturn("/terminology/ValueSet");
    when(request.getAttribute("org.springframework.web.util.ServletRequestPathUtils.PATH"))
        .thenReturn(null);
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
    when(request.getHttpServletMapping()).thenReturn(mock(HttpServletMapping.class));
    when(request.getHttpServletMapping().getMatchValue()).thenReturn("/terminology/ValueSet");
    when(response.getOutputStream()).thenReturn(servletOutputStream);
    filter.doFilterInternal(request, response, filterChain);
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader(eq("Content-Type"), eq("text/plain;charset=UTF-8"));
    verify(response).setHeader(eq(HttpHeaders.WWW_AUTHENTICATE), eq("Basic realm=\"API\""));
    assertEquals("Invalid or missing credentials", outputStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void rejectsWhenDecodedCredentialsMalformed() throws Exception {
    String expectedApiKey = "valid-key";
    ApiKeyFilter filter = new ApiKeyFilter(expectedApiKey);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain filterChain = mock(FilterChain.class);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ServletOutputStream servletOutputStream =
        new ServletOutputStream() {
          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(WriteListener writeListener) {}

          @Override
          public void write(int b) throws IOException {
            outputStream.write(b);
          }
        };
    String malformed = "invalidformat";
    String base64 = Base64.getEncoder().encodeToString(malformed.getBytes(StandardCharsets.UTF_8));
    when(request.getRequestURI()).thenReturn("/terminology/ValueSet");
    when(request.getServletPath()).thenReturn("/terminology/ValueSet");
    when(request.getAttribute("org.springframework.web.util.ServletRequestPathUtils.PATH"))
        .thenReturn(null);
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + base64);
    when(request.getHttpServletMapping()).thenReturn(mock(HttpServletMapping.class));
    when(request.getHttpServletMapping().getMatchValue()).thenReturn("/terminology/ValueSet");
    when(response.getOutputStream()).thenReturn(servletOutputStream);
    filter.doFilterInternal(request, response, filterChain);
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader(eq("Content-Type"), eq("text/plain;charset=UTF-8"));
    verify(response).setHeader(eq(HttpHeaders.WWW_AUTHENTICATE), eq("Basic realm=\"API\""));
    assertEquals("Invalid or missing credentials", outputStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void rejectsWhenPasswordDoesNotMatchExpected() throws Exception {
    String expectedApiKey = "valid-key";
    ApiKeyFilter filter = new ApiKeyFilter(expectedApiKey);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain filterChain = mock(FilterChain.class);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ServletOutputStream servletOutputStream =
        new ServletOutputStream() {
          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(WriteListener writeListener) {}

          @Override
          public void write(int b) throws IOException {
            outputStream.write(b);
          }
        };
    String credentials = "user:wrong-password";
    String base64 =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    when(request.getRequestURI()).thenReturn("/terminology/ValueSet");
    when(request.getServletPath()).thenReturn("/terminology/ValueSet");
    when(request.getAttribute("org.springframework.web.util.ServletRequestPathUtils.PATH"))
        .thenReturn(null);
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + base64);
    when(request.getHttpServletMapping()).thenReturn(mock(HttpServletMapping.class));
    when(request.getHttpServletMapping().getMatchValue()).thenReturn("/terminology/ValueSet");
    when(response.getOutputStream()).thenReturn(servletOutputStream);
    filter.doFilterInternal(request, response, filterChain);
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader(eq("Content-Type"), eq("text/plain;charset=UTF-8"));
    verify(response).setHeader(eq(HttpHeaders.WWW_AUTHENTICATE), eq("Basic realm=\"API\""));
    assertEquals("Invalid or missing credentials", outputStream.toString(StandardCharsets.UTF_8));
  }
}
