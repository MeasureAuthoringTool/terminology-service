package gov.cms.madie.terminology.util;

import gov.cms.madie.models.mapping.CodeSystemEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class TerminologyServiceUtilTest {
  private CodeSystemEntry cse;
  private CodeSystemEntry.Version v1;
  private CodeSystemEntry.Version v2;

  @BeforeEach
  void setUp() {
    v1 = CodeSystemEntry.Version.builder().fhir("01012021").vsac("2021-01").build();
    v2 = CodeSystemEntry.Version.builder().fhir("05052022").vsac("2022-05").build();
    cse = CodeSystemEntry.builder().versions(List.of(v1, v2)).build();
  }

  @Test
  void testGetCodeSystemVersionForQdmModel() {
    String version = TerminologyServiceUtil.getCodeSystemVersion(cse, v1.getFhir(), "QDM");
    assertThat(version, is(equalTo(v1.getVsac())));
  }

  @Test
  void testGetCodeSystemVersionForFhirModel() {
    String version = TerminologyServiceUtil.getCodeSystemVersion(cse, v1.getFhir(), "FHIR");
    assertThat(version, is(equalTo(v1.getFhir())));
  }

  @Test
  void testGetCodeSystemVersionWhenCodeSystemIsNull() {
    cse.setVersions(List.of());
    String version = TerminologyServiceUtil.getCodeSystemVersion(null, v1.getFhir(), "FHIR");
    assertThat(version, is(equalTo(v1.getFhir())));
  }

  @Test
  void testGetCodeSystemVersionWhenFhirVersionIsNull() {
    cse.setVersions(List.of());
    String version = TerminologyServiceUtil.getCodeSystemVersion(cse, null, "FHIR");
    assertThat(version, is(equalTo(null)));
  }

  @Test
  void testGetCodeSystemVersionWhenEquivalentQdmVersionIsNull() {
    v1.setVsac(null);
    String version = TerminologyServiceUtil.getCodeSystemVersion(cse, v1.getFhir(), "QDM");
    assertThat(version, is(equalTo(null)));
  }
}
