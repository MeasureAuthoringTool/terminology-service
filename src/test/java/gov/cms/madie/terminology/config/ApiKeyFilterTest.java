package gov.cms.madie.terminology.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    @Test
    void testDoFilterInternalWithValidApiKey() throws Exception {
        String expectedApiKey = "valid-key";
        String headerName = "X-API-Key";
        ApiKeyFilter filter = new ApiKeyFilter(expectedApiKey, headerName);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/terminology/ValueSet");
        when(request.getServletPath()).thenReturn("/terminology/ValueSet");
        when(request.getAttribute(
                "org.springframework.web.util.ServletRequestPathUtils.PATH")).thenReturn(null);
        when(request.getHeader(headerName)).thenReturn(expectedApiKey);
        when(request.getHttpServletMapping()).thenReturn(mock(HttpServletMapping.class));
        when(request.getHttpServletMapping().getMatchValue()).thenReturn("/terminology/ValueSet");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilterInternalWithInvalidApiKey() throws Exception {
        String expectedApiKey = "valid-key";
        String headerName = "X-API-Key";
        ApiKeyFilter filter = new ApiKeyFilter(expectedApiKey, headerName);

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
                    public void setWriteListener(WriteListener writeListener) {
                    }

                    @Override
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }
                };

        when(request.getRequestURI()).thenReturn("/terminology/ValueSet");
        when(request.getServletPath()).thenReturn("/terminology/ValueSet");
        when(request.getAttribute(
                "org.springframework.web.util.ServletRequestPathUtils.PATH")).thenReturn(null);

        when(request.getHeader(headerName)).thenReturn("invalid-key");
        when(request.getHttpServletMapping()).thenReturn(mock(HttpServletMapping.class));
        when(request.getHttpServletMapping().getMatchValue()).thenReturn("/terminology/ValueSet");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response)
                .setHeader(
                        eq("Content-Type"), eq("text/plain;charset=UTF-8"));
        assertEquals("Invalid or missing API key", outputStream.toString(StandardCharsets.UTF_8));
    }
}