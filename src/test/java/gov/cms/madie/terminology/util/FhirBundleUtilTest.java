package gov.cms.madie.terminology.util;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.CodeSystem;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FhirBundleUtilTest {

  private FhirContext fhirContext;

  private static final String SAMPLE_VALUESET_JSON =
      """
      {
        "resourceType": "ValueSet",
        "id": "vs-1",
        "url": "http://example.com/ValueSet/test",
        "version": "1.0.0",
        "name": "TestValueSet",
        "status": "active"
      }
      """;

  @BeforeEach
  void setUp() {
    fhirContext = FhirContext.forR4();
  }

  @Test
  void createValueSetBundleReturnsSearchSetBundle() {
    Bundle result = FhirBundleUtil.createValueSetBundle(fhirContext, SAMPLE_VALUESET_JSON);

    assertNotNull(result);
    assertThat(result.getType(), is(equalTo(Bundle.BundleType.SEARCHSET)));
  }

  @Test
  void createValueSetBundleSetsTotalToOne() {
    Bundle result = FhirBundleUtil.createValueSetBundle(fhirContext, SAMPLE_VALUESET_JSON);

    assertThat(result.getTotal(), is(equalTo(1)));
  }

  @Test
  void createValueSetBundleContainsOneEntry() {
    Bundle result = FhirBundleUtil.createValueSetBundle(fhirContext, SAMPLE_VALUESET_JSON);

    assertThat(result.getEntry().size(), is(equalTo(1)));
  }

  @Test
  void createValueSetBundleEntryContainsValueSetResource() {
    Bundle result = FhirBundleUtil.createValueSetBundle(fhirContext, SAMPLE_VALUESET_JSON);

    assertThat(
        result.getEntryFirstRep().getResource().getResourceType().name(), is(equalTo("ValueSet")));
  }

  @Test
  void createValueSetBundlePreservesValueSetProperties() {
    Bundle result = FhirBundleUtil.createValueSetBundle(fhirContext, SAMPLE_VALUESET_JSON);

    org.hl7.fhir.r4.model.ValueSet valueSet =
        (org.hl7.fhir.r4.model.ValueSet) result.getEntryFirstRep().getResource();
    assertThat(valueSet.getUrl(), is(equalTo("http://example.com/ValueSet/test")));
    assertThat(valueSet.getVersion(), is(equalTo("1.0.0")));
    assertThat(valueSet.getName(), is(equalTo("TestValueSet")));
  }

  @Test
  void createCodeSystemBundleReturnsSearchSetBundle() {
    List<CodeSystem> codeSystems = List.of(buildCodeSystem("1", "cs-1"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    assertNotNull(result);
    assertThat(result.getType(), is(equalTo(Bundle.BundleType.SEARCHSET)));
  }

  @Test
  void createCodeSystemBundleSetsTotalToCodeSystemCount() {
    List<CodeSystem> codeSystems =
        List.of(
            buildCodeSystem("1", "cs-1"),
            buildCodeSystem("2", "cs-2"),
            buildCodeSystem("3", "cs-3"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    assertThat(result.getTotal(), is(equalTo(3)));
  }

  @Test
  void createCodeSystemBundleContainsOneEntryPerCodeSystem() {
    List<CodeSystem> codeSystems =
        List.of(buildCodeSystem("1", "cs-1"), buildCodeSystem("2", "cs-2"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    assertThat(result.getEntry().size(), is(equalTo(2)));
  }

  @Test
  void createCodeSystemBundlePreservesCodeSystemId() {
    List<CodeSystem> codeSystems = List.of(buildCodeSystem("test-id", "cs-1"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getId(), is(equalTo("test-id")));
  }

  @Test
  void createCodeSystemBundlePreservesCodeSystemUrl() {
    List<CodeSystem> codeSystems =
        List.of(buildCodeSystem("1", "http://example.com/CodeSystem/custom"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getUrl(), is(equalTo("http://example.com/CodeSystem/custom")));
  }

  @Test
  void createCodeSystemBundlePreservesCodeSystemName() {
    List<CodeSystem> codeSystems = List.of(buildCodeSystem("1", "cs-1"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getName(), is(equalTo("codeSys1")));
  }

  @Test
  void createCodeSystemBundlePreservesCodeSystemTitle() {
    List<CodeSystem> codeSystems = List.of(buildCodeSystemWithTitle("1", "cs-1", "Custom Title"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getTitle(), is(equalTo("Custom Title")));
  }

  @Test
  void createCodeSystemBundlePreservesCodeSystemVersion() {
    List<CodeSystem> codeSystems = List.of(buildCodeSystem("1", "cs-1"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getVersion(), is(equalTo("1.0")));
  }

  @Test
  void createCodeSystemBundlePreservesCodeSystemOidAsIdentifier() {
    List<CodeSystem> codeSystems = List.of(buildCodeSystem("1", "cs-1"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getIdentifier().size(), is(equalTo(1)));
    assertThat(fhirCodeSystem.getIdentifier().get(0).getValue(), is(equalTo("2.16.840.1.1")));
  }

  @Test
  void createCodeSystemBundleHandlesEmptyCodeSystemList() {
    Bundle result = FhirBundleUtil.createCodeSystemBundle(Collections.emptyList());

    assertNotNull(result);
    assertThat(result.getType(), is(equalTo(Bundle.BundleType.SEARCHSET)));
    assertThat(result.getTotal(), is(equalTo(0)));
    assertTrue(result.getEntry().isEmpty());
  }

  @Test
  void createCodeSystemBundleHandlesMultipleCodeSystems() {
    List<CodeSystem> codeSystems =
        List.of(
            buildCodeSystem("1", "cs-1"),
            buildCodeSystem("2", "cs-2"),
            buildCodeSystem("3", "cs-3"));

    Bundle result = FhirBundleUtil.createCodeSystemBundle(codeSystems);

    assertThat(result.getEntry().size(), is(equalTo(3)));
    for (Bundle.BundleEntryComponent entry : result.getEntry()) {
      assertThat(entry.getResource().getResourceType().name(), is(equalTo("CodeSystem")));
    }
  }

  @Test
  void createCodeSystemBundleConvertsAllCodeSystemProperties() {
    CodeSystem cs =
        CodeSystem.builder()
            .id("custom-id")
            .fullUrl("http://example.com/CodeSystem/custom")
            .name("customCodeSystem")
            .title("Custom Code System Title")
            .versionId("2.5")
            .oid("2.16.840.1.999")
            .version(CodeSystem.Version.builder().fhirVersion("4.0.1").build())
            .lastUpdated(Instant.now())
            .isLatestVersion(true)
            .build();

    Bundle result = FhirBundleUtil.createCodeSystemBundle(List.of(cs));

    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem =
        (org.hl7.fhir.r4.model.CodeSystem) result.getEntryFirstRep().getResource();
    assertThat(fhirCodeSystem.getId(), is(equalTo("custom-id")));
    assertThat(fhirCodeSystem.getUrl(), is(equalTo("http://example.com/CodeSystem/custom")));
    assertThat(fhirCodeSystem.getName(), is(equalTo("customCodeSystem")));
    assertThat(fhirCodeSystem.getTitle(), is(equalTo("Custom Code System Title")));
    assertThat(fhirCodeSystem.getVersion(), is(equalTo("2.5")));
    assertThat(fhirCodeSystem.getIdentifier().get(0).getValue(), is(equalTo("2.16.840.1.999")));
  }

  @Test
  void createValueSetBundleHandlesInvalidJsonGracefully() {
    assertThrows(
        Exception.class, () -> FhirBundleUtil.createValueSetBundle(fhirContext, "invalid json"));
  }

  private CodeSystem buildCodeSystem(String id, String fullUrl) {
    return CodeSystem.builder()
        .id(id)
        .fullUrl(fullUrl)
        .name("codeSys" + id)
        .oid("2.16.840.1.1")
        .versionId("1.0")
        .version(CodeSystem.Version.builder().fhirVersion("4.0.1").build())
        .lastUpdated(Instant.now())
        .build();
  }

  private CodeSystem buildCodeSystemWithTitle(String id, String fullUrl, String title) {
    return CodeSystem.builder()
        .id(id)
        .fullUrl(fullUrl)
        .name("codeSys" + id)
        .title(title)
        .oid("2.16.840.1.1")
        .versionId("1.0")
        .version(CodeSystem.Version.builder().fhirVersion("4.0.1").build())
        .lastUpdated(Instant.now())
        .build();
  }
}
