package gov.cms.madie.terminology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the downstream excel-export (NestJS) service. Reads the base URL from
 * configuration (see application.yml: {@code excel-export-service.base-url}).
 */
@Configuration
@ConfigurationProperties(prefix = "excel-export-service")
@Data
public class ExcelExportServiceConfig {

  private String baseUrl;

  @Bean(name = "excelExportRestTemplate")
  public RestTemplate excelExportRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }
}
