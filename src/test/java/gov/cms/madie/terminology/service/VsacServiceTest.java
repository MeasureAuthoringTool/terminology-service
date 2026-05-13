package gov.cms.madie.terminology.service;

import com.okta.commons.lang.Collections;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.models.cql.terminology.VsacCode.VsacError;
import org.hl7.fhir.r4.model.ValueSet;

import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.helpers.TestHelpers;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.repositories.UmlsUserRepository;
import gov.cms.madie.terminology.webclient.TerminologyServiceWebClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VsacServiceTest {

  @Mock TerminologyServiceWebClient terminologyServiceWebClient;

  @Mock CodeSystemRepository codeSystemRepository;

  @Mock gov.cms.madie.terminology.mapper.VsacToFhirValueSetMapper vsacToFhirValueSetMapper;

  @Mock UmlsUserRepository umlsUserRepository;

  @InjectMocks VsacService vsacService;

  List<CqlCode> cqlCodes;
  VsacCode vsacCode;
  List<CodeSystem> codeSystems;
  private ValueSetsSearchCriteria valueSetsSearchCriteria;
  private RetrieveMultipleValueSetsResponse svsValueSet;
  private UmlsUser umlsUser;
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";
  private static final String QDM_MODEL = "QDM";
  private static final String FHIR_MODEL = "FHIR";

  @BeforeEach
  public void setUp() throws JAXBException {
    cqlCodes = new ArrayList<>();
    CqlCode cqlCode =
        CqlCode.builder()
            .name("preop")
            .codeId("'P'")
            .codeSystem(
                CqlCode.CqlCodeSystem.builder()
                    .oid(
                        // Enclosing url/oid in single quotes to match most common format.
                        "'https://terminology.hl7.org/CodeSystem/v3-ActPriority'")
                    .name("ActPriority:HL7V3.0_2021-03")
                    .version("'HL7V3.0_2021-03'")
                    .build())
            .build();
    cqlCodes.add(cqlCode);

    vsacCode = new VsacCode();
    vsacCode.setMessage("This is a valid code");
    vsacCode.setStatus("ok");

    codeSystems = new ArrayList<>();
    var codeSystem =
        CodeSystem.builder()
            .name("ActPriority")
            .oid("'urn:oid:1.2.3.4.5.6.7.8.9'") // Enclosing single quotes match API usage.
            .fullUrl("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(CodeSystem.Version.builder().vsacVersion("2.3").fhirVersion("2.3").build())
            .build();
    codeSystems.add(codeSystem);

    File file = TestHelpers.getTestResourceFile("/value-sets/svs_office_visit.xml");
    JAXBContext jaxbContext = JAXBContext.newInstance(RetrieveMultipleValueSetsResponse.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    svsValueSet = (RetrieveMultipleValueSetsResponse) jaxbUnmarshaller.unmarshal(file);
    valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .profile("eCQM Update 2030-05-05")
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113883.3.464.1003.101.12.1001")
                        .build()))
            .build();

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
  }

  @Test
  void testAValidCodeFromVsacFhir() {
    // Verifies oids wrapped in single quotes are unwrapped.
    when(codeSystemRepository.findAllByFullUrl(
            eq("https://terminology.hl7.org/CodeSystem/v3-ActPriority")))
        .thenReturn(codeSystems);
    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/ActPriority/Version/HL7V3.0_2021-03/Code/P/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testAValidCodeFromVsacQdm() {
    when(codeSystemRepository.findAllByOid(eq("urn:oid:1.1.1.1"))).thenReturn(codeSystems);
    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/ActPriority/Version/HL7V3.0_2021-03/Code/P/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> testCode =
        List.of(
            CqlCode.builder()
                .name("preop")
                .codeId("'P'")
                .codeSystem(
                    CqlCode.CqlCodeSystem.builder()
                        .oid("'urn:oid:1.1.1.1'")
                        .name("ActPriority:HL7V3.0_2021-03")
                        .version("'HL7V3.0_2021-03'")
                        .build())
                .build());
    List<CqlCode> result = vsacService.validateCodes(testCode, umlsUser, QDM_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testCodeSystemNotFoundFromVsac() {
    when(codeSystemRepository.findAllByFullUrl(anyString())).thenReturn(codeSystems);
    vsacCode.setStatus("error");

    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("800");
    vsacErrorResultSet.setErrDesc("CodeSystem not found");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    vsacCode.setErrors(vsacError);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("CodeSystem not found", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testCodeSystemVersionNotFoundFromVsac() {
    when(codeSystemRepository.findAllByFullUrl(anyString())).thenReturn(codeSystems);
    vsacCode.setStatus("error");

    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("801");
    vsacErrorResultSet.setErrDesc("CodeSystem version not found");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    vsacCode.setErrors(vsacError);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("CodeSystem version not found", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testCodeNotFoundFromVsac() {
    when(codeSystemRepository.findAllByFullUrl(anyString())).thenReturn(codeSystems);
    vsacCode.setStatus("error");

    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("802");
    vsacErrorResultSet.setErrDesc("Code not found");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    vsacCode.setErrors(vsacError);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).isValid());
    assertEquals("Code not found", result.get(0).getErrorMessage());
  }

  @Test
  void testVsacCommunicationError() {
    when(codeSystemRepository.findAllByFullUrl(anyString())).thenReturn(codeSystems);
    VsacCode badRequest = new VsacCode();
    badRequest.setStatus(
        "400"); // VSAC's response to using the updated Basic Authn scheme on code validation.
    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/ActPriority/Version/HL7V3.0_2021-03/Code/P/Info"), anyString()))
        .thenReturn(badRequest);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).isValid());
    assertTrue(result.get(0).getErrorMessage().contains("Communication Error with VSAC"));
  }

  @Test
  void testIfCqlCodesListIsEmpty() {
    List<CqlCode> result = vsacService.validateCodes(new ArrayList<>(), umlsUser, FHIR_MODEL);
    assertEquals(0, result.size());
  }

  @Test
  void testIfCqlCodeDoesNotContainOid() {
    cqlCodes.get(0).getCodeSystem().setOid(null);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Code system URL is required", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfThereIsNoAssociatedCodeSystem() {
    when(codeSystemRepository.findAllByFullUrl(anyString())).thenReturn(List.of());
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemIsNotInVsac() {
    var notInVsacCodeSystem =
        CodeSystem.builder()
            .name("ActPriority")
            .oid("NOT.IN.VSAC")
            .fullUrl("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(CodeSystem.Version.builder().vsacVersion("2.3").fhirVersion("2.3").build())
            .build();
    when(codeSystemRepository.findAllByFullUrl(anyString()))
        .thenReturn(List.of(notInVsacCodeSystem));
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testIfCodeIdIsNotProvided() {
    cqlCodes.get(0).setCodeId(null);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).isValid());
    assertEquals("Code Id is required", result.get(0).getErrorMessage());
  }

  @Test
  void testIfCodeSystemDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsNotProvided() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    var codeSystemNoVersion =
        CodeSystem.builder()
            .name("ActPriority")
            .oid("urn:oid:1.2.3.4.5.6.7.8.9")
            .fullUrl("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(null)
            .build();
    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(codeSystemNoVersion));
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, QDM_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemHasAKnownVersionButTheVsacValueIsNull() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    var codeSystemNullVsac =
        CodeSystem.builder()
            .name("ActPriority")
            .oid("urn:oid:1.2.3.4.5.6.7.8.9")
            .fullUrl("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(CodeSystem.Version.builder().fhirVersion("2.3").vsacVersion(null).build())
            .build();
    when(codeSystemRepository.findAllByFullUrl(anyString()))
        .thenReturn(List.of(codeSystemNullVsac));
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsProvided() {
    var codeSystemNoVersion =
        CodeSystem.builder()
            .name("ActPriority")
            .oid("urn:oid:1.2.3.4.5.6.7.8.9")
            .fullUrl("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(null)
            .build();
    when(codeSystemRepository.findAllByFullUrl(anyString()))
        .thenReturn(List.of(codeSystemNoVersion));

    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  public void testGetValueSets() {

    when(terminologyServiceWebClient.getValueSet(any(), any(), any(), any(), any(), any()))
        .thenReturn(svsValueSet);

    List<RetrieveMultipleValueSetsResponse> vsacValueSets =
        vsacService.getValueSets(valueSetsSearchCriteria, umlsUser);

    RetrieveMultipleValueSetsResponse.DescribedValueSet describedValueSet =
        vsacValueSets.get(0).getDescribedValueSet();
    assertThat(
        describedValueSet.getID(),
        is(equalTo(valueSetsSearchCriteria.getValueSetParams().get(0).getOid())));
    assertThat(describedValueSet.getDisplayName(), is(equalTo("Office Visit")));
    assertThat(describedValueSet.getConceptList().getConcepts().size(), is(equalTo(16)));
  }

  @Test
  public void testVersionMapping() {
    CqlCode snomedCode =
        CqlCode.builder()
            .name("37687000")
            .codeId("37687000")
            .codeSystem(
                CqlCode.CqlCodeSystem.builder()
                    .oid("http://snomed.info/sct")
                    .name("SNOMEDCT")
                    .version("http://snomed.info/sct/731000124108/version/20220301")
                    .build())
            .build();

    var snomedCodeSystem =
        CodeSystem.builder()
            .name("SNOMEDCT")
            .oid("urn:oid:2.16.840.1.113883.6.96")
            .fullUrl("http://snomed.info/sct")
            .version(
                CodeSystem.Version.builder()
                    .vsacVersion("2022-03")
                    .fhirVersion("http://snomed.info/sct/731000124108/version/20220301")
                    .build())
            .build();
    when(codeSystemRepository.findAllByFullUrl(anyString())).thenReturn(List.of(snomedCodeSystem));

    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/SNOMEDCT/Version/2022-03/Code/37687000/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(List.of(snomedCode), umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  public void testSaveUmlsUser() {
    ArgumentCaptor<UmlsUser> captor = ArgumentCaptor.forClass(UmlsUser.class);
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));
    UmlsUser saved = vsacService.saveUmlsUser(TEST_API_KEY, TEST_HARP_ID);
    verify(umlsUserRepository, times(1)).save(captor.capture());
    UmlsUser captored = captor.getValue();
    assertNotNull(captored);
    assertEquals(TEST_HARP_ID, captored.getApiKey());
    assertEquals(TEST_API_KEY, saved.getApiKey());
  }

  @Test
  public void testFindByHarpId() {
    Optional<UmlsUser> optional = Optional.of(umlsUser);
    doReturn(optional).when(umlsUserRepository).findByHarpId(anyString());
    Optional<UmlsUser> result = vsacService.findByHarpId(TEST_HARP_ID);
    assertNotNull(result);
    assertEquals(TEST_HARP_ID, result.get().getHarpId());
    assertEquals(TEST_API_KEY, result.get().getApiKey());
  }

  @Test
  public void testValidateUmlsInformationWhenUmlsApiKeyIsNotAvailable() {
    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);

    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    when(optionalUmlsUser.get().getApiKey()).thenReturn(null);
    assertFalse(vsacService.validateUmlsInformation("test_user"));
  }

  @Test
  public void testValidateCodesForQDMFormat() {
    CqlCode snomedCode =
        CqlCode.builder()
            .name("37687000")
            .codeId("37687000")
            .codeSystem(
                CqlCode.CqlCodeSystem.builder()
                    .oid("urn:oid:2.16.840.1.113883.6.96")
                    .name("SNOMEDCT")
                    .version("http://snomed.info/sct/731000124108/version/20220301")
                    .build())
            .build();

    var snomedCodeSystem =
        CodeSystem.builder()
            .name("SNOMEDCT")
            .oid("urn:oid:2.16.840.1.113883.6.96")
            .fullUrl("http://snomed.info/sct")
            .version(
                CodeSystem.Version.builder()
                    .vsacVersion("2022-03")
                    .fhirVersion("http://snomed.info/sct/731000124108/version/20220301")
                    .build())
            .build();
    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(snomedCodeSystem));

    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/SNOMEDCT/Version/2022-03/Code/37687000/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(List.of(snomedCode), umlsUser, QDM_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testVerifyUmlsAccessUmlsUserNotFound() {
    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(Optional.empty());
    Exception exception =
        assertThrows(
            VsacUnauthorizedException.class, () -> vsacService.verifyUmlsAccess(TEST_API_KEY));
    assertThat(exception.getMessage(), is(equalTo("Please login to UMLS before proceeding")));
  }

  @Test
  void testVerifyUmlsAccessUmlsUserApiKeyIsMissing() {
    UmlsUser umlsUserCopy = umlsUser.toBuilder().apiKey(null).build();
    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(Optional.of(umlsUserCopy));
    Exception exception =
        assertThrows(
            VsacUnauthorizedException.class, () -> vsacService.verifyUmlsAccess(TEST_API_KEY));
    assertThat(exception.getMessage(), is(equalTo("Please login to UMLS before proceeding")));
  }

  @Test
  void testVerifyUmlsAccess() {
    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(Optional.of(umlsUser));
    UmlsUser user = vsacService.verifyUmlsAccess(TEST_API_KEY);
    assertThat(user.getHarpId(), is(equalTo(TEST_HARP_ID)));
    assertThat(user.getApiKey(), is(equalTo(TEST_API_KEY)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemMappingAbsent() {
    assertThat(
        vsacService.getCodeStatus(
            Code.builder().codeSystemOid("oid").fhirVersion("version").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemNotInSvs() {
    assertThat(
        vsacService.getCodeStatus(
            Code.builder().codeSystemOid("NOT.IN.VSAC1").fhirVersion("version").build(),
            TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemVersionEmpty() {
    assertThat(
        vsacService.getCodeStatus(Code.builder().codeSystemOid("oid").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemVersionForVsacIsNull() {
    assertThat(
        vsacService.getCodeStatus(Code.builder().codeSystemOid("oid").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusActive() {
    Code code =
        Code.builder()
            .name("1222766008")
            .codeSystem("ABC")
            .fhirVersion("abc.info/20230901")
            .svsVersion("2023-09")
            .display("American Joint Committee on Cancer stage IIA")
            .codeSystemOid("1.2.3.4.96")
            .build();
    var codeResultSet = new VsacCode.VsacDataResultSet();
    codeResultSet.setActive("Yes");
    var codeData = new VsacCode.VsacData();
    codeData.setResultSet(List.of(codeResultSet));
    VsacCode vsacCode = new VsacCode();
    vsacCode.setStatus("ok");
    vsacCode.setData(codeData);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    CodeStatus status = vsacService.getCodeStatus(code, TEST_API_KEY);
    assertThat(status, is(equalTo(CodeStatus.ACTIVE)));
  }

  @Test
  void testGetCodeStatusInactive() {
    Code code =
        Code.builder()
            .name("1222766008")
            .codeSystem("ABC")
            .fhirVersion("abc.info/20230901")
            .svsVersion("2023-09")
            .display("American Joint Committee on Cancer stage IIA")
            .codeSystemOid("1.2.3.4.96")
            .build();
    var codeResultSet = new VsacCode.VsacDataResultSet();
    codeResultSet.setActive("No");
    var codeData = new VsacCode.VsacData();
    codeData.setResultSet(List.of(codeResultSet));
    VsacCode vsacCode = new VsacCode();
    vsacCode.setStatus("ok");
    vsacCode.setData(codeData);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    CodeStatus status = vsacService.getCodeStatus(code, TEST_API_KEY);
    assertThat(status, is(equalTo(CodeStatus.INACTIVE)));
  }

  @Test
  void testGetCodeStatusIfCodeNotFoundInSvs() {
    Code code =
        Code.builder()
            .name("1222766008")
            .codeSystem("ABC")
            .fhirVersion("abc.info/20230901")
            .svsVersion("2023-09")
            .display("American Joint Committee on Cancer stage IIA")
            .codeSystemOid("1.2.3.4.96")
            .build();
    var codeResultSet = new VsacCode.VsacDataResultSet();
    codeResultSet.setActive("No");
    var codeData = new VsacCode.VsacData();
    codeData.setResultSet(List.of(codeResultSet));
    VsacCode vsacCode = new VsacCode();
    vsacCode.setStatus("non-ok");
    vsacCode.setData(codeData);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    CodeStatus status = vsacService.getCodeStatus(code, TEST_API_KEY);
    assertThat(status, is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testUserUmlsLogout() {
    when(umlsUserRepository.deleteByHarpId(anyString())).thenReturn(Optional.of(umlsUser));
    boolean loggedOut = vsacService.logoutUMLSUser(umlsUser.getHarpId());
    assertTrue(loggedOut);
  }

  @Test
  void testUserUmlsLogoutFailed() {
    when(umlsUserRepository.deleteByHarpId(anyString())).thenReturn(Optional.empty());
    boolean loggedOut = vsacService.logoutUMLSUser(umlsUser.getHarpId());
    assertFalse(loggedOut);
  }

  /* branch test on validateUmlsInformation(), line 49:
   * return umlsUser.isPresent() && !StringUtils.isBlank(umlsUser.get().getApiKey());
   */
  @Test
  public void testValidateUmlsInformationWhenUmlsApiKeyIsAvailable() {
    // return the pre-built umlsUser from setUp which has TEST_API_KEY
    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(Optional.of(umlsUser));
    assertTrue(vsacService.validateUmlsInformation(umlsUser.getHarpId()));
  }

  /* branch test on validateUmlsInformation(), line 49:
   * when !umlsUser.isPresent()
   */
  @Test
  void validateUmlsInformationUmlsUserNotFound() {
    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(Optional.empty());
    boolean isValid = vsacService.validateUmlsInformation(umlsUser.getHarpId());
    assertFalse(isValid);
  }

  /* covers convertToFHIRValueSet(), line 65:
   * return vsacToFhirValueSetMapper.convertToFHIRValueSet(vsacValueSetResponse);
   */
  @Test
  void convertToFHIRValueSet() {
    ValueSet valueSet = vsacService.convertToFHIRValueSet(svsValueSet);
    assertNull(valueSet);
  }

  /* test for validateCode(), line 111
   * return;
   */
  @Test
  void validateCodeReturnsEarlyWhenCodeSystemIsNull() throws Exception {
    CqlCode cqlCode = CqlCode.builder().name("test").codeId("validCodeId").codeSystem(null).build();
    UmlsUser umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    List<CqlCode> result = vsacService.validateCodes(List.of(cqlCode), umlsUser, FHIR_MODEL);

    // Assert: no error message, no invalid flag set
    assertThat(result.size(), is(1));
    assertNull(result.get(0).getErrorMessage());
    assertTrue(result.get(0).isValid());
  }

  /* test for buildCodeSystemVersion(), lines 220-221
   * return latestCodeSystemVersion.get().getVersion().getVsacVersion();
   */
  @Test
  void
      buildCodeSystemVersionReturnsLatestVsacVersionWhenCodeSystemVersionBlankAndLatestVersionPresent()
          throws Exception {
    // Arrange
    CqlCode cqlCode = new CqlCode();
    cqlCode.setCodeSystem(
        CqlCode.CqlCodeSystem.builder()
            .version("") // blank version
            .build());
    CodeSystem.Version version =
        CodeSystem.Version.builder().vsacVersion("2026-03-15").fhirVersion("2026-03-15").build();
    CodeSystem latestCodeSystem =
        CodeSystem.builder().isLatestVersion(true).version(version).build();
    List<CodeSystem> codeSystems = List.of(latestCodeSystem);
    Method method =
        VsacService.class.getDeclaredMethod("buildCodeSystemVersion", CqlCode.class, List.class);
    method.setAccessible(true);
    String result =
        (String) method.invoke(new VsacService(null, null, null, null), cqlCode, codeSystems);
    // Assert
    assertEquals("2026-03-15", result);
  }

  /* branch coverage for buildCodeSystemVersion(), line 220
   * !StringUtils.isNotBlank(latestCodeSystemVersion.get().getVersion().getVsacVersion())
   */
  @Test
  void buildCodeSystemVersionReturnsEmpty_whenLatestVsacVersionBlank_branchCoverage()
      throws Exception {
    CqlCode cqlCode = new CqlCode();
    cqlCode.setCodeSystem(
        CqlCode.CqlCodeSystem.builder()
            .version("") // blank version
            .build());
    CodeSystem.Version version =
        CodeSystem.Version.builder()
            .vsacVersion("") // blank vsacVersion
            .fhirVersion("2026-03-15")
            .build();
    CodeSystem latestCodeSystem =
        CodeSystem.builder().isLatestVersion(true).version(version).build();
    List<CodeSystem> codeSystems = List.of(latestCodeSystem);
    Method method =
        VsacService.class.getDeclaredMethod("buildCodeSystemVersion", CqlCode.class, List.class);
    method.setAccessible(true);
    String result =
        (String) method.invoke(new VsacService(null, null, null, null), cqlCode, codeSystems);
    // Assert: result is empty, error message is set
    assertEquals("", result);
    assertFalse(cqlCode.getCodeSystem().isValid());
    assertEquals("Unable to find a code system version", cqlCode.getCodeSystem().getErrorMessage());
  }

  /* coverage for buildCodeSystemVersion(), line 243:
   * ? cqlCodeSystemVersion
   */
  @Test
  void buildCodeSystemVersionReturnsCqlCodeSystemVersionWhenVsacVersionBlank() throws Exception {
    CqlCode cqlCode = new CqlCode();
    cqlCode.setCodeSystem(
        CqlCode.CqlCodeSystem.builder()
            .version("2026-03-15") // user-provided version
            .build());
    CodeSystem.Version version =
        CodeSystem.Version.builder()
            .vsacVersion("") // blank vsacVersion
            .fhirVersion("2026-03-15") // matches CQL version
            .build();
    CodeSystem codeSystem = CodeSystem.builder().version(version).build();
    List<CodeSystem> codeSystems = List.of(codeSystem);
    Method method =
        VsacService.class.getDeclaredMethod("buildCodeSystemVersion", CqlCode.class, List.class);
    method.setAccessible(true);
    String result =
        (String) method.invoke(new VsacService(null, null, null, null), cqlCode, codeSystems);
    // Assert: result is sanitized CQL code system version
    assertEquals("2026-03-15", result);
  }

  /* branch coverage for buildVsacErrorMessage(), line 262:
   * && StringUtils.isNumeric(vsacCode.getErrors().getResultSet().get(0).getErrCode()))
   */
  @Test
  void buildVsacErrorMessageHandlesNonNumericErrCode() throws Exception {
    CqlCode cqlCode =
        CqlCode.builder()
            .name("test")
            .codeId("P")
            .codeSystem(CqlCode.CqlCodeSystem.builder().oid("urn:oid:1.2.3.4.5.6.7.8.9").build())
            .build();
    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("NOT_NUM"); // non-numeric
    vsacErrorResultSet.setErrDesc("Some weird error");
    VsacCode.VsacError vsacError = new VsacCode.VsacError();
    vsacError.setResultSet(List.of(vsacErrorResultSet));
    VsacCode vsacCode = new VsacCode();
    vsacCode.setStatus("error");
    vsacCode.setErrors(vsacError);
    Method method =
        VsacService.class.getDeclaredMethod("buildVsacErrorMessage", CqlCode.class, VsacCode.class);
    method.setAccessible(true);
    method.invoke(new VsacService(null, null, null, null), cqlCode, vsacCode);
    // Assert: code is invalid
    assertFalse(cqlCode.isValid());
    assertNull(cqlCode.getErrorMessage()); // error message is not set for uncaught error
  }

  /*
   * branch coverage for line 272:  else if (errorCode == 802)
   */
  @Test
  void buildVsacErrorMessage_handlesErrorCode802_branchCoverage() throws Exception {
    CqlCode cqlCode =
        CqlCode.builder()
            .name("test")
            .codeId("P")
            .codeSystem(CqlCode.CqlCodeSystem.builder().oid("urn:oid:1.2.3.4.5.6.7.8.9").build())
            .build();
    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("803"); // error code 802
    vsacErrorResultSet.setErrDesc("Code not found");
    VsacCode.VsacError vsacError = new VsacCode.VsacError();
    vsacError.setResultSet(List.of(vsacErrorResultSet));
    VsacCode vsacCode = new VsacCode();
    vsacCode.setStatus("error");
    vsacCode.setErrors(vsacError);
    Method method =
        VsacService.class.getDeclaredMethod("buildVsacErrorMessage", CqlCode.class, VsacCode.class);
    method.setAccessible(true);
    method.invoke(new VsacService(null, null, null, null), cqlCode, vsacCode);
    // Assert: code is invalid, error message is set to error description
    assertFalse(cqlCode.isValid());
    assertNull(cqlCode.getErrorMessage());
  }

  @Test
  void saveUmlsUserNormalizesHarpIdToLowerCase() {
    ArgumentCaptor<UmlsUser> captor = ArgumentCaptor.forClass(UmlsUser.class);
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));
    vsacService.saveUmlsUser("MixedCaseUser", TEST_API_KEY);
    verify(umlsUserRepository).save(captor.capture());
    assertEquals("mixedcaseuser", captor.getValue().getHarpId());
  }

  @Test
  void findByHarpIdNormalizesInputToLowerCase() {
    doReturn(Optional.of(umlsUser)).when(umlsUserRepository).findByHarpId(anyString());
    vsacService.findByHarpId("MixedCaseUser");
    verify(umlsUserRepository).findByHarpId(eq("mixedcaseuser"));
  }

  @Test
  void logoutUMLSUserNormalizesUserNameToLowerCase() {
    doReturn(Optional.of(umlsUser)).when(umlsUserRepository).deleteByHarpId(anyString());
    vsacService.logoutUMLSUser("MixedCaseUser");
    verify(umlsUserRepository).deleteByHarpId(eq("mixedcaseuser"));
  }
}
