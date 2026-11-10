package gov.cms.madie.terminology.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import gov.cms.madie.terminology.TerminologyServiceApplication;
import gov.cms.madie.terminology.clients.UserRoleConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.actuate.web.WebMvcEndpointManagementContextConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CacheInspectionEndpointTest {

  @Test
  void configuredCachesShouldExposeAccurateStatsAndPreservePolicy() {
    CacheManager manager = new TerminologyServiceApplication().cacheManager();
    assertThat(manager.getCacheNames()).containsExactly("manifest-list");
    CaffeineCache cache = (CaffeineCache) manager.getCache("manifest-list");
    assertThat(cache).isNotNull();
    var nativeCache = cache.getNativeCache();
    assertThat(nativeCache.policy().isRecordingStats()).isTrue();
    assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(500);
    assertThat(
            nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.HOURS))
        .isEqualTo(24);
    nativeCache.put("present", "value");
    nativeCache.getIfPresent("present");
    nativeCache.getIfPresent("missing");
    Map<?, ?> stats =
        (Map<?, ?>) new CacheInspectionEndpoint(manager).inspect().get("manifest-list");
    assertThat(stats.get("hitCount")).isEqualTo(1L);
    assertThat(stats.get("missCount")).isEqualTo(1L);
    assertThat(stats.get("hitRate")).isEqualTo("50.00%");
    assertThat(stats.get("entryCount")).isEqualTo(1L);
    assertThat(stats.containsKey("keys")).isFalse();
    assertThat(stats.containsKey("estimatedMemoryBytes")).isFalse();
    for (int i = 0; i < 500; i++) {
      nativeCache.put(i, "value");
    }
    nativeCache.cleanUp();
    stats = (Map<?, ?>) new CacheInspectionEndpoint(manager).inspect().get("manifest-list");
    assertThat(stats.get("entryCount")).isEqualTo(500L);
    assertThat(stats.get("evictionCount")).isEqualTo(1L);
  }

  @Test
  void inspectShouldHandleEmptyCachesAndDisabledStats() {
    CaffeineCacheManager manager = new CaffeineCacheManager("empty");
    manager.setCaffeine(Caffeine.newBuilder());
    Map<String, Object> result = new CacheInspectionEndpoint(manager).inspect();
    Map<?, ?> stats = (Map<?, ?>) result.get("empty");
    assertThat(stats.get("hitRate")).isEqualTo("N/A");
    assertThat(stats.get("statsEnabled")).isEqualTo(false);
    assertThat(stats.get("hitCount")).isEqualTo(0L);
    assertThat(stats.get("missCount")).isEqualTo(0L);
    Map<?, ?> memory = (Map<?, ?>) result.get("jvmMemory");
    assertThat((Long) memory.get("heapUsedBytes")).isGreaterThanOrEqualTo(0);
    assertThat((Long) memory.get("heapCommittedBytes"))
        .isGreaterThanOrEqualTo((Long) memory.get("heapUsedBytes"));
    assertThat((Long) memory.get("heapMaxBytes")).isNotZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"default", "local", "aws", "it"})
  void actuatorShouldExposeInspectorUsingApplicationConfiguration(String profile) {
    new WebApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.profiles.active=" + profile)
        .withConfiguration(
            AutoConfigurations.of(
                EndpointAutoConfiguration.class,
                WebEndpointAutoConfiguration.class,
                DispatcherServletAutoConfiguration.class,
                WebMvcEndpointManagementContextConfiguration.class,
                WebMvcAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class))
        .withUserConfiguration(SecurityConfig.class)
        .withBean(UserRoleConverter.class, () -> mock(UserRoleConverter.class))
        .withBean(JwtDecoder.class, () -> mock(JwtDecoder.class))
        .withBean(ApiKeyFilter.class)
        .withBean(CacheManager.class, () -> new TerminologyServiceApplication().cacheManager())
        .withBean(CacheInspectionEndpoint.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(WebEndpointsSupplier.class).getEndpoints())
                  .anyMatch(
                      endpoint -> endpoint.getEndpointId().toString().equals("cache-inspection"));
              var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
              mvc.perform(get("/api/actuator/cache-inspection").contextPath("/api"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$['manifest-list'].statsEnabled").value(true))
                  .andExpect(jsonPath("$.jvmMemory.heapUsedBytes").isNumber())
                  .andExpect(jsonPath("$.jvmMemory.heapMaxBytes").isNumber());
            });
  }
}
