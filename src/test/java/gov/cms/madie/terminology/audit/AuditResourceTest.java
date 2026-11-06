package gov.cms.madie.terminology.audit;

import gov.cms.madie.terminology.audit.AuditResource.ResourceType;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditResourceTest {

  @Test
  void fromCodeSystemMapsIdentityFieldsInOrder() {
    CodeSystem codeSystem =
        CodeSystem.builder()
            .id("LOINCversion2.40")
            .title("LOINC Title")
            .name("LOINC")
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("http://loinc.org")
            .version(CodeSystem.Version.builder().fhirVersion("2.40").vsacVersion("2.41").build())
            .versionId("1")
            .lastUpdated(Instant.now())
            .isLatestVersion(true)
            .build();

    AuditResource resource = AuditResource.from(codeSystem);

    assertEquals(ResourceType.CODE_SYSTEM, resource.type());
    assertEquals("LOINCversion2.40", resource.id());
    assertEquals(
        List.of("title", "name", "oid", "fullUrl", "fhirVersion", "vsacVersion"),
        List.copyOf(resource.attributes().keySet()));
    assertEquals("LOINC Title", resource.attributes().get("title"));
    assertEquals("LOINC", resource.attributes().get("name"));
    assertEquals("urn:oid:2.16.840.1.113883.6.1", resource.attributes().get("oid"));
    assertEquals("http://loinc.org", resource.attributes().get("fullUrl"));
    assertEquals("2.40", resource.attributes().get("fhirVersion"));
    assertEquals("2.41", resource.attributes().get("vsacVersion"));
  }

  @Test
  void fromCodeSystemOmitsNullFieldsAndNullVersion() {
    CodeSystem codeSystem = CodeSystem.builder().id("cs-1").name("SNOMEDCT").build();

    AuditResource resource = AuditResource.from(codeSystem);

    assertEquals(Map.of("name", "SNOMEDCT"), resource.attributes());
  }

  @Test
  void fromValueSetMapsIdentityFieldsAndExcludesExpansion() {
    MadieValueSet valueSet =
        MadieValueSet.builder()
            .id("vs-1")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
            .version("20240101")
            .oid("1.2.3")
            .displayName("Office Visit")
            .status("active")
            .totalConcepts(42)
            .valueSet("{\"resourceType\":\"ValueSet\"}")
            .build();

    AuditResource resource = AuditResource.from(valueSet);

    assertEquals(ResourceType.VALUE_SET, resource.type());
    assertEquals("vs-1", resource.id());
    assertEquals(
        List.of("url", "version", "oid", "displayName"),
        List.copyOf(resource.attributes().keySet()));
    assertEquals("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", resource.attributes().get("url"));
    assertEquals("20240101", resource.attributes().get("version"));
    assertEquals("1.2.3", resource.attributes().get("oid"));
    assertEquals("Office Visit", resource.attributes().get("displayName"));
  }

  @Test
  void bulkUsesAllAsIdWithDescription() {
    AuditResource resource = AuditResource.bulk(ResourceType.VALUE_SET, "All IGs");

    assertEquals(ResourceType.VALUE_SET, resource.type());
    assertEquals(AuditResource.ALL, resource.id());
    assertEquals(Map.of("description", "All IGs"), resource.attributes());
  }
}
