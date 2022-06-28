package gov.cms.madie.terminology.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;

@Configuration
public class FhirContextConfig {

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }
}
