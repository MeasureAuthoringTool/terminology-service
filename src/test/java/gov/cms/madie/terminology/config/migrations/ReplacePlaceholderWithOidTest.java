package gov.cms.madie.terminology.config.migrations;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.TxTerminologyServiceWebClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplacePlaceholderWithOidTest {

  private static final String TEST_URL = "http://example.com/fhir/CodeSystem/test";
  private static final String PLACEHOLDER_OID = "NOT.IN.VSAC##test";
  private static final String TEST_OID = "urn:oid:1.2.3.4";

  @Captor private ArgumentCaptor<List<CodeSystem>> savedSystems;

  @Mock private MongoTemplate mongoTemplate;
  @Mock private TxTerminologyServiceWebClient txTerminologyServiceWebClient;
  @Mock private CodeSystemRepository codeSystemRepository;

  private final FhirContext fhirContext = FhirContext.forR4();
  private ReplacePlaceholderWithOid migration;

  @BeforeEach
  void setUp() {
    migration = new ReplacePlaceholderWithOid();
  }

  @Test
  void replacesPlaceholderOidsWithTxFhirDataAndMarksThemNotInVsac() {
    CodeSystem first = codeSystem("first", PLACEHOLDER_OID, TEST_URL, false);
    CodeSystem second = codeSystem("second", "NOT.IN.VSAC##another", TEST_URL, true);
    when(codeSystemRepository.findAll()).thenReturn(List.of(first, second));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null))
        .thenReturn(codeSystemBundle(TEST_OID));

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals(2, savedSystems.getValue().size());
    assertEquals(TEST_OID, savedSystems.getValue().get(0).getOid());
    assertEquals(TEST_OID, savedSystems.getValue().get(1).getOid());
    assertFalse(savedSystems.getValue().get(0).isInVsac());
    assertFalse(savedSystems.getValue().get(1).isInVsac());
    verify(txTerminologyServiceWebClient, times(1)).getCodeSystemBundle(TEST_URL, null);
  }

  @Test
  void leavesExistingOidsUnchangedAndMarksThemInVsac() {
    CodeSystem existing = codeSystem("existing", TEST_OID, TEST_URL, false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(existing));

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals(TEST_OID, savedSystems.getValue().get(0).getOid());
    assertTrue(savedSystems.getValue().get(0).isInVsac());
    verifyNoInteractions(txTerminologyServiceWebClient);
  }

  @Test
  void retainsOidPlaceholderWhenResponseHasNoIdentifier() {
    CodeSystem placeholder = codeSystem("placeholder", PLACEHOLDER_OID, TEST_URL, false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(placeholder));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null))
        .thenReturn(codeSystemBundleWithoutIdentifier());

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals("NOT.IN.VSAC", savedSystems.getValue().get(0).getOid());
    assertFalse(savedSystems.getValue().get(0).isInVsac());
  }

  @Test
  void retainsOidPlaceholderWhenResponseIdentifierIsNotAnOid() {
    CodeSystem placeholder = codeSystem("placeholder", PLACEHOLDER_OID, TEST_URL, false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(placeholder));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null))
        .thenReturn(codeSystemBundle("invalid-identifier"));

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals("NOT.IN.VSAC", savedSystems.getValue().get(0).getOid());
    assertFalse(savedSystems.getValue().get(0).isInVsac());
  }

  @Test
  void retainsOidPlaceholderWhenResponseContainsMultipleOids() {
    CodeSystem placeholder = codeSystem("placeholder", PLACEHOLDER_OID, TEST_URL, false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(placeholder));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null))
        .thenReturn(codeSystemBundle("urn:oid:1.2.3,urn:oid:4.5.6"));

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals("NOT.IN.VSAC", savedSystems.getValue().get(0).getOid());
    assertFalse(savedSystems.getValue().get(0).isInVsac());
  }

  @Test
  void retainsOidPlaceholderWhenResponseCannotBeParsed() {
    CodeSystem placeholder = codeSystem("placeholder", PLACEHOLDER_OID, TEST_URL, false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(placeholder));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null)).thenReturn("not-json");

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals("NOT.IN.VSAC", savedSystems.getValue().get(0).getOid());
    assertFalse(savedSystems.getValue().get(0).isInVsac());
  }

  @Test
  void retainsOidPlaceholderWhenBundleHasNoEntries() {
    CodeSystem placeholder = codeSystem("placeholder", PLACEHOLDER_OID, TEST_URL, false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(placeholder));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null))
        .thenReturn(fhirContext.newJsonParser().encodeResourceToString(new Bundle()));

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);

    verify(codeSystemRepository).saveAll(savedSystems.capture());
    assertEquals("NOT.IN.VSAC", savedSystems.getValue().get(0).getOid());
    assertFalse(savedSystems.getValue().get(0).isInVsac());
  }

  @Test
  void rollbackRestoresTheOriginalCodeSystems() {
    CodeSystem original = codeSystem("original", PLACEHOLDER_OID, TEST_URL, true);
    when(codeSystemRepository.findAll()).thenReturn(List.of(original));
    when(txTerminologyServiceWebClient.getCodeSystemBundle(TEST_URL, null))
        .thenReturn(codeSystemBundle(TEST_OID));

    migration.apply(
        mongoTemplate, txTerminologyServiceWebClient, fhirContext, codeSystemRepository);
    migration.rollback(mongoTemplate, codeSystemRepository);

    verify(codeSystemRepository, times(2)).saveAll(savedSystems.capture());
    CodeSystem restored = savedSystems.getAllValues().get(1).get(0);
    assertEquals(PLACEHOLDER_OID, restored.getOid());
    assertTrue(restored.isInVsac());
    assertEquals(original.getVersion(), restored.getVersion());
    assertNotSame(original, restored);
  }

  private CodeSystem codeSystem(String id, String oid, String url, boolean inVsac) {
    return CodeSystem.builder()
        .id(id)
        .fullUrl(url)
        .name("Test Code System")
        .oid(oid)
        .version(CodeSystem.Version.builder().fhirVersion("1.0.0").vsacVersion("1.0.0").build())
        .isInVsac(inVsac)
        .build();
  }

  private String codeSystemBundle(String identifierValue) {
    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem = new org.hl7.fhir.r4.model.CodeSystem();
    fhirCodeSystem.addIdentifier(new Identifier().setValue(identifierValue));
    Bundle bundle = new Bundle();
    bundle.addEntry().setResource(fhirCodeSystem);
    return fhirContext.newJsonParser().encodeResourceToString(bundle);
  }

  private String codeSystemBundleWithoutIdentifier() {
    Bundle bundle = new Bundle();
    bundle.addEntry().setResource(new org.hl7.fhir.r4.model.CodeSystem());
    return fhirContext.newJsonParser().encodeResourceToString(bundle);
  }
}
