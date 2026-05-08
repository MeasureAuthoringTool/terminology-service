package gov.cms.madie.terminology;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.mongock.runner.springboot.EnableMongock;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableMongock
@EnableAsync
public class TerminologyServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TerminologyServiceApplication.class, args);
  }

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("manifest-list");
    cacheManager.setCaffeine(
        Caffeine.newBuilder().maximumSize(500).expireAfterWrite(24, TimeUnit.HOURS));
    return cacheManager;
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {

      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedMethods("PUT", "POST", "GET", "DELETE")
            .allowedOrigins(
                "http://localhost:9000",
                "https://dev-madie.hcqis.org",
                "https://test-madie.hcqis.org",
                "https://impl-madie.hcqis.org ",
                "https://dev.madie.internal.cms.gov",
                "https://test.madie.internal.cms.gov",
                "https://impl.madie.internal.cms.gov",
                "https://madie.cms.gov");
      }
    };
  }
}
