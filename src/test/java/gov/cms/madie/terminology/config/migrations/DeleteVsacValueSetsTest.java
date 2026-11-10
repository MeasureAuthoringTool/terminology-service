package gov.cms.madie.terminology.config.migrations;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteVsacValueSetsTest {

  @Mock private ValueSetExpansionRepository valueSetExpansionRepository;
  @Mock private FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock private Environment environment;

  @InjectMocks private DeleteVsacValueSets migration;

  private final FhirContext fhirContext = FhirContext.forR4Cached();

  @Test
  void applyDeletesOnlyValueSetsFoundInVsac() {
    String apiKey = "test-api-key";
    MadieValueSet inVsac =
        MadieValueSet.builder()
            .id("vsac-1")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/123")
            .valueSet("value-set-1")
            .build();
    MadieValueSet notInVsac =
        MadieValueSet.builder()
            .id("not-vsac")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/456")
            .valueSet("value-set-2")
            .build();

    when(environment.getProperty("code-system-refresh-task.terminology-key")).thenReturn(apiKey);
    when(valueSetExpansionRepository.findByUrlStartingWith("http://cts.nlm.nih.gov/fhir/"))
        .thenReturn(List.of(inVsac, notInVsac));
    when(fhirTerminologyServiceWebClient.searchValueSets(eq(apiKey), anyMap()))
        .thenReturn(
            bundleJsonFor("http://cts.nlm.nih.gov/fhir/ValueSet/123"), bundleJsonForEmpty());

    migration.apply(
        valueSetExpansionRepository, fhirTerminologyServiceWebClient, fhirContext, environment);

    verify(valueSetExpansionRepository, times(1)).delete(inVsac);
    verify(valueSetExpansionRepository, never()).delete(notInVsac);
  }

  @Test
  void applyDoesNothingWhenNoMadieValueSetsMatchPrefix() {
    when(environment.getProperty("code-system-refresh-task.terminology-key"))
        .thenReturn("test-api-key");
    when(valueSetExpansionRepository.findByUrlStartingWith("http://cts.nlm.nih.gov/fhir/"))
        .thenReturn(List.of());

    migration.apply(
        valueSetExpansionRepository, fhirTerminologyServiceWebClient, fhirContext, environment);

    verify(valueSetExpansionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void applySkipsBlankUrlsAndContinuesProcessing() {
    MadieValueSet blankUrlValueSet =
        MadieValueSet.builder().id("blank-url").url(" ").valueSet("value-set-blank").build();
    MadieValueSet inVsacValueSet =
        MadieValueSet.builder()
            .id("vsac-2")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/999")
            .valueSet("value-set-999")
            .build();

    when(environment.getProperty("code-system-refresh-task.terminology-key"))
        .thenReturn("test-api-key");
    when(valueSetExpansionRepository.findByUrlStartingWith("http://cts.nlm.nih.gov/fhir/"))
        .thenReturn(List.of(blankUrlValueSet, inVsacValueSet));
    when(fhirTerminologyServiceWebClient.searchValueSets(eq("test-api-key"), anyMap()))
        .thenReturn(bundleJsonFor("http://cts.nlm.nih.gov/fhir/ValueSet/999"));

    migration.apply(
        valueSetExpansionRepository, fhirTerminologyServiceWebClient, fhirContext, environment);

    verify(valueSetExpansionRepository, times(1)).delete(inVsacValueSet);
    verify(valueSetExpansionRepository, never()).delete(blankUrlValueSet);
  }

  @Test
  void applyDoesNotDeleteWhenVsacVerificationThrows() {
    MadieValueSet failingValueSet =
        MadieValueSet.builder()
            .id("fails-check")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/777")
            .valueSet("value-set-777")
            .build();

    when(environment.getProperty("code-system-refresh-task.terminology-key"))
        .thenReturn("test-api-key");
    when(valueSetExpansionRepository.findByUrlStartingWith("http://cts.nlm.nih.gov/fhir/"))
        .thenReturn(List.of(failingValueSet));
    when(fhirTerminologyServiceWebClient.searchValueSets(eq("test-api-key"), anyMap()))
        .thenThrow(new RuntimeException("VSAC unavailable"));

    migration.apply(
        valueSetExpansionRepository, fhirTerminologyServiceWebClient, fhirContext, environment);

    verify(valueSetExpansionRepository, never()).delete(failingValueSet);
  }

  @Test
  void rollbackDoesNothingWhenNoDeletedValueSetsExist() {
    migration.rollback(valueSetExpansionRepository);

    verify(valueSetExpansionRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void rollbackRestoresDeletedRecords() {
    MadieValueSet deleted =
        MadieValueSet.builder()
            .id("deleted-1")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/789")
            .valueSet("value-set-3")
            .build();

    when(environment.getProperty("code-system-refresh-task.terminology-key"))
        .thenReturn("test-api-key");
    when(valueSetExpansionRepository.findByUrlStartingWith("http://cts.nlm.nih.gov/fhir/"))
        .thenReturn(List.of(deleted));
    when(fhirTerminologyServiceWebClient.searchValueSets(eq("test-api-key"), anyMap()))
        .thenReturn(bundleJsonFor("http://cts.nlm.nih.gov/fhir/ValueSet/789"));

    migration.apply(
        valueSetExpansionRepository, fhirTerminologyServiceWebClient, fhirContext, environment);
    migration.rollback(valueSetExpansionRepository);

    verify(valueSetExpansionRepository).saveAll(List.of(deleted));
  }

  private String bundleJsonFor(String url) {
    return
        """
        {
          "resourceType": "Bundle",
          "entry": [
            {
              "resource": {
                "resourceType": "ValueSet",
                "id": "value-set-id",
                "url": "%s",
                "status": "active"
              }
            }
          ]
        }
        """
        .formatted(url);
  }

  private String bundleJsonForEmpty() {
    return
    """
        {
          "resourceType": "Bundle",
          "entry": []
        }
        """;
  }
}
