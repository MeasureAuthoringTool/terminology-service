package gov.cms.madie.terminology.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HapiFhirConfig {
  @Value("${hapi-fhir-url}")
  private String hapiFhirUrl;

  @Bean
  @Qualifier("hapiClient")
  public IGenericClient createHapiClient(@Autowired FhirContext fhirContext) {
    return fhirContext.newRestfulGenericClient(hapiFhirUrl);
  }
}
