package cms.gov.madie.terminology.service;

import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import com.okta.commons.lang.Collections;
import gov.cms.madiejavamodels.cql.terminology.CqlCode;
import gov.cms.madiejavamodels.cql.terminology.VsacCode;
import gov.cms.madiejavamodels.cql.terminology.VsacCode.VsacError;
import gov.cms.madiejavamodels.mappingData.CodeSystemEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VsacServiceTest {

  @Mock TerminologyServiceWebClient terminologyServiceWebClient;

  @Mock MappingService mappingService;

  @InjectMocks VsacService vsacService;

  List<CqlCode> cqlCodes;
  VsacCode vsacCode;
  List<CodeSystemEntry> codeSystemEntries;

  @BeforeEach
  public void setUp() {
    cqlCodes = new ArrayList<>();
    CqlCode cqlCode =
        CqlCode.builder()
            .name("preop")
            .codeId("'P'")
            .codeSystem(
                CqlCode.CqlCodeSystem.builder()
                    .oid("'https://terminology.hl7.org/CodeSystem/v3-ActPriority'")
                    .name("ActPriority:HL7V3.0_2021-03")
                    .version("'HL7V3.0_2021-03'")
                    .build())
            .build();
    cqlCodes.add(cqlCode);

    vsacCode = new VsacCode();
    vsacCode.setMessage("This is a valid code");
    vsacCode.setStatus("ok");

    codeSystemEntries = new ArrayList<>();
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2.3");
    version.setFhir("2.3");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ActPriority")
            .oid("1.2.3.4.5.6.7.8.9")
            .url("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(Collections.toList(version))
            .build();
    codeSystemEntries.add(codeSystemEntry);
  }

  @Test
  void testAValidCodeFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testCodeSystemNotFoundFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    vsacCode.setStatus("error");

    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("800");
    vsacErrorResultSet.setErrDesc("CodeSystem not found");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    vsacCode.setErrors(vsacError);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("CodeSystem not found", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testCodeSystemVersionNotFoundFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    vsacCode.setStatus("error");

    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("801");
    vsacErrorResultSet.setErrDesc("CodeSystem version not found");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    vsacCode.setErrors(vsacError);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("CodeSystem version not found", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testCodeNotFoundFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    vsacCode.setStatus("error");

    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("802");
    vsacErrorResultSet.setErrDesc("Code not found");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    vsacCode.setErrors(vsacError);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).isValid());
    assertEquals("Code not found", result.get(0).getErrorMessage());
  }

  @Test
  void testIfCqlCodesListIsEmpty() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(new ArrayList<>(), "Test-TGT-Token");
    assertEquals(0, result.size());
  }

  @Test
  void testIfCqlCodeDoesNotContainOid() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    cqlCodes.get(0).getCodeSystem().setOid(null);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Code system URL is required", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfThereIsNoAssociatedCodeSystemEntry() {
    codeSystemEntries.get(0).setUrl("test-Url");
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Invalid Code system", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemIsNotInVsac() {
    codeSystemEntries.get(0).setOid("NOT.IN.VSAC");
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testIfCodeIdIsNotProvided() {
    cqlCodes.get(0).setCodeId(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).isValid());
    assertEquals("Code Id is required", result.get(0).getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsNotProvided() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    codeSystemEntries.get(0).setVersion(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsProvided() {
    codeSystemEntries.get(0).setVersion(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, "Test-TGT-Token");
    assertTrue(result.get(0).isValid());
  }
}
