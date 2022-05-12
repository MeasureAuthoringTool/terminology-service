package cms.gov.madie.terminology.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@Configuration
public class FhirContextAndIParserConfig {

  @Bean
  public FhirContext fhirContext() {
    FhirContext ctx = FhirContext.forR4();
    return ctx;
  }

  @Bean
  public IParser iParser() {
    FhirContext ctx = fhirContext();
    IParser parser = ctx.newJsonParser();
    return parser;
  }
}
