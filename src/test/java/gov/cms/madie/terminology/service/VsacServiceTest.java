package gov.cms.madie.terminology.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.commons.lang.Collections;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.models.cql.terminology.VsacCode.VsacError;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import org.hl7.fhir.r4.model.ValueSet;

import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.helpers.TestHelpers;
import gov.cms.madie.terminology.models.UmlsUser;
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

  @Mock MappingService mappingService;

  @Mock gov.cms.madie.terminology.mapper.VsacToFhirValueSetMapper vsacToFhirValueSetMapper;

  @Mock UmlsUserRepository umlsUserRepository;

  @InjectMocks VsacService vsacService;

  List<CqlCode> cqlCodes;
  VsacCode vsacCode;
  List<CodeSystemEntry> codeSystemEntries;
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
            .oid("urn:oid:1.2.3.4.5.6.7.8.9")
            .url("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .versions(Collections.toList(version))
            .build();
    codeSystemEntries.add(codeSystemEntry);

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
  void testAValidCodeFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/ActPriority/Version/HL7V3.0_2021-03/Code/P/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testCodeSystemNotFoundFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
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
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
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
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
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
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
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
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(new ArrayList<>(), umlsUser, FHIR_MODEL);
    assertEquals(0, result.size());
  }

  @Test
  void testIfCqlCodeDoesNotContainOid() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    cqlCodes.get(0).getCodeSystem().setOid(null);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Code system URL is required", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfThereIsNoAssociatedCodeSystemEntry() {
    codeSystemEntries.get(0).setUrl("test-Url");
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Invalid Code system", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemIsNotInVsac() {
    codeSystemEntries.get(0).setOid("NOT.IN.VSAC");
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testIfCodeIdIsNotProvided() {
    cqlCodes.get(0).setCodeId(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).isValid());
    assertEquals("Code Id is required", result.get(0).getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsNotProvided() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    codeSystemEntries.get(0).setVersions(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryHasAKnownVersionButTheVsacValueIsNull() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    codeSystemEntries.get(0).getVersions().get(0).setVsac(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsProvided() {
    codeSystemEntries.get(0).setVersions(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);

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
  public void testVersionMapping() throws JsonProcessingException {
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

    String snomedMapping =
        "{"
            + "\"oid\": \"urn:oid:2.16.840.1.113883.6.96\","
            + "\"url\": \"http://snomed.info/sct\","
            + "\"name\": \"SNOMEDCT\","
            + "\"versions\": ["
            + "  {"
            + "    \"vsac\": \"2022-03\","
            + "        \"fhir\": \"http://snomed.info/sct/731000124108/version/20220301\""
            + "  }"
            + "]"
            + "}";
    ObjectMapper objectMapper = new ObjectMapper();
    CodeSystemEntry snomedCsEntry = objectMapper.readValue(snomedMapping, CodeSystemEntry.class);
    when(mappingService.getCodeSystemEntries()).thenReturn(List.of(snomedCsEntry));

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
  public void testValidateCodesForQDMFormat() throws JsonProcessingException {
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

    String snomedMapping =
        "{"
            + "\"oid\": \"urn:oid:2.16.840.1.113883.6.96\","
            + "\"url\": \"http://snomed.info/sct\","
            + "\"name\": \"SNOMEDCT\","
            + "\"versions\": ["
            + "  {"
            + "    \"vsac\": \"2022-03\","
            + "        \"fhir\": \"http://snomed.info/sct/731000124108/version/20220301\""
            + "  }"
            + "]"
            + "}";
    ObjectMapper objectMapper = new ObjectMapper();
    CodeSystemEntry snomedCsEntry = objectMapper.readValue(snomedMapping, CodeSystemEntry.class);
    when(mappingService.getCodeSystemEntries()).thenReturn(List.of(snomedCsEntry));

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
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(null);
    assertThat(
        vsacService.getCodeStatus(
            Code.builder().codeSystemOid("oid").fhirVersion("version").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemNotInSvs() {
    var cse = CodeSystemEntry.builder().oid("NOT.IN.VSAC1").build();
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(cse);
    assertThat(
        vsacService.getCodeStatus(
            Code.builder().codeSystemOid("oid").fhirVersion("version").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemVersionEmpty() {
    var cse = CodeSystemEntry.builder().oid("1.1.1.1").versions(List.of()).build();
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(cse);
    assertThat(
        vsacService.getCodeStatus(Code.builder().codeSystemOid("oid").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusIfCodeSystemVersionForVsacIsNull() {
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac(null);
    version.setFhir("https://fhir-version");
    var cse = CodeSystemEntry.builder().oid("1.1.1.1").versions(List.of(version)).build();
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(cse);
    assertThat(
        vsacService.getCodeStatus(Code.builder().codeSystemOid("oid").build(), TEST_API_KEY),
        is(equalTo(CodeStatus.NA)));
  }

  @Test
  void testGetCodeStatusActive() {
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2023-09");
    version.setFhir("abc.info/20230901");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ABC")
            .oid("urn:oid:1.2.3.4.96")
            .url("abc.info")
            .versions(Collections.toList(version))
            .build();
    Code code =
        Code.builder()
            .name("1222766008")
            .codeSystem("ABC")
            .fhirVersion("abc.info/20230901")
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
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(codeSystemEntry);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    CodeStatus status = vsacService.getCodeStatus(code, TEST_API_KEY);
    assertThat(status, is(equalTo(CodeStatus.ACTIVE)));
  }

  @Test
  void testGetCodeStatusInactive() {
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2023-09");
    version.setFhir("abc.info/20230901");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ABC")
            .oid("urn:oid:1.2.3.4.96")
            .url("abc.info")
            .versions(Collections.toList(version))
            .build();
    Code code =
        Code.builder()
            .name("1222766008")
            .codeSystem("ABC")
            .fhirVersion("abc.info/20230901")
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
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(codeSystemEntry);
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    CodeStatus status = vsacService.getCodeStatus(code, TEST_API_KEY);
    assertThat(status, is(equalTo(CodeStatus.INACTIVE)));
  }

  @Test
  void testGetCodeStatusIfCodeNotFoundInSvs() {
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2023-09");
    version.setFhir("abc.info/20230901");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ABC")
            .oid("urn:oid:1.2.3.4.96")
            .url("abc.info")
            .versions(Collections.toList(version))
            .build();
    Code code =
        Code.builder()
            .name("1222766008")
            .codeSystem("ABC")
            .fhirVersion("abc.info/20230901")
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
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(codeSystemEntry);
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

  /* covers buildCodeSystemVersion(), line 238:
   * ? cqlCodeSystemVersion (when optionalCodeSystemVersion.get().getVsac() == null)
   */
  @Test
  void testCqlProvidedVersionMatchesFhirButVsacNull_usesCqlVersion() {
    CqlCode cqlCode =
        CqlCode.builder()
            .name("preop")
            .codeId("'P'")
            .codeSystem(
                CqlCode.CqlCodeSystem.builder()
                    .oid("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
                    .name("ActPriority:HL7V3.0_2021-03")
                    .version("'HL7V3.0_2021-03'")
                    .build())
            .build();

    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac(null);
    version.setFhir("HL7V3.0_2021-03");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ActPriority")
            .oid("urn:oid:1.2.3.4.5.6.7.8.9")
            .url("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .versions(Collections.toList(version))
            .build();

    when(mappingService.getCodeSystemEntries()).thenReturn(List.of(codeSystemEntry));

    VsacCode ok = new VsacCode();
    ok.setStatus("ok");
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(ok);

    List<CqlCode> result = vsacService.validateCodes(List.of(cqlCode), umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());

    // verify the generated path used the CQL-supplied version (sanitized, without quotes)
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(terminologyServiceWebClient).getCode(pathCaptor.capture(), anyString());
    String usedPath = pathCaptor.getValue();
    assertTrue(usedPath.contains("/Version/HL7V3.0_2021-03/"));
  }

  /* covers buildCodeSystemVersion(), line 255:
   * return codeSystemEntryVersion.get(0).getVsac();
   */
  @Test
  void testWhenCqlVersionMissing_usesFirstVersionVsac() {
    // CQL code has no version provided
    CqlCode cqlCode =
        CqlCode.builder()
            .name("preop")
            .codeId("'P'")
            .codeSystem(
                CqlCode.CqlCodeSystem.builder()
                    .oid("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
                    .name("ActPriority:HL7V3.0_2021-03")
                    .version(null)
                    .build())
            .build();

    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2.3");
    version.setFhir("2.3");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ActPriority")
            .oid("urn:oid:1.2.3.4.5.6.7.8.9")
            .url("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .versions(Collections.toList(version))
            .build();

    when(mappingService.getCodeSystemEntries()).thenReturn(List.of(codeSystemEntry));

    VsacCode ok = new VsacCode();
    ok.setStatus("ok");
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(ok);

    List<CqlCode> result = vsacService.validateCodes(List.of(cqlCode), umlsUser, FHIR_MODEL);
    assertTrue(result.get(0).isValid());

    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(terminologyServiceWebClient).getCode(pathCaptor.capture(), anyString());
    String usedPath = pathCaptor.getValue();
    assertTrue(usedPath.contains("/Version/2.3/"));
  }

  /* covers private void buildVsacErrorMessage(),
   * lines 291 - 295, and line 285:
   * if !(StringUtils.isNumeric(vsacCode.getStatus()))
   */
  @Test
  void testUncaughtVsacErrorSetsInvalid() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);

    VsacCode bad = new VsacCode();
    bad.setStatus("NON_NUMERIC_STATUS");
    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("NOT_NUM");
    vsacErrorResultSet.setErrDesc("Some weird error");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    bad.setErrors(vsacError);

    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(bad);

    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).isValid());
  }

  /* branch coverage for private void buildVsacErrorMessage(), line 278:
   * if !(errorCode == 802)
   */
  @Test
  void testNumericButUnknownVsacErrorCodeDoesNotSetInvalid() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);

    VsacCode bad = new VsacCode();
    bad.setStatus("error");
    VsacCode.VsacErrorResultSet vsacErrorResultSet = new VsacCode.VsacErrorResultSet();
    vsacErrorResultSet.setErrCode("999");
    vsacErrorResultSet.setErrDesc("Some numeric but unknown error");
    VsacError vsacError = new VsacError();
    vsacError.setResultSet((Collections.toList(vsacErrorResultSet)));
    bad.setErrors(vsacError);

    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(bad);

    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    // current implementation does not mark the CQL code invalid for unknown numeric error codes
    assertTrue(result.get(0).isValid());
  }

  /* branch coverage for validateCodes(), line 108:
   * if (codeId == null || TerminologyServiceUtil.sanitizeInput(codeId).isBlank()) {
   */
  @Test
  void testCodeIdSanitizedBlankSetsError() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    // simulate code id that becomes blank after sanitize (e.g., "''")
    cqlCodes.get(0).setCodeId("''");
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertFalse(result.get(0).isValid());
    assertEquals("Code Id is required", result.get(0).getErrorMessage());
  }

  /* branch coverage for validateCodes(), line 96:
   * if !(cqlCode.getCodeSystem() != null)
   */
  @Test
  void validateCodesCodeSystemNull() {
    cqlCodes = new ArrayList<>();
    CqlCode cqlCode = CqlCode.builder().name("preop").codeId("'P'").build();
    cqlCodes.add(cqlCode);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);

    List<CqlCode> codes = vsacService.validateCodes(cqlCodes, umlsUser, FHIR_MODEL);
    assertNotNull(codes);
    assertEquals(cqlCode, codes.get(0));
  }

  /* coverage for getSvsCodeSystemVersion(), line 169:
   * return code.getSvsVersion();
   * when StringUtils.isNotBlank(code.getSvsVersion())
   */
  @Test
  void getSvsCodeSystemVersion_returnsCodeSvsVersion_whenNotBlank() throws Exception {
    Code code = Code.builder().build();
    code.setSvsVersion("test-svs-version");
    java.lang.reflect.Method method =
        VsacService.class.getDeclaredMethod("getSvsCodeSystemVersion", Code.class);
    method.setAccessible(true);
    String result = (String) method.invoke(vsacService, code);
    assertEquals("test-svs-version", result);
  }

  /* branch coverage for getSvsCodeSystemVersion(), line 185:
   * if (version == null || version.getVsac() == null)
   */
  @Test
  void getSvsCodeSystemVersionReturnsNullWhenVsacIsNullOnMatchingVersion() throws Exception {
    Code code = Code.builder().fhirVersion("match.fhir").codeSystemOid("9.9.9.9").build();
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac(null);
    version.setFhir("match.fhir");
    var cse =
        CodeSystemEntry.builder().oid("9.9.9.9").versions(Collections.toList(version)).build();
    when(mappingService.getCodeSystemEntryByOid(anyString())).thenReturn(cse);

    java.lang.reflect.Method method =
        VsacService.class.getDeclaredMethod("getSvsCodeSystemVersion", Code.class);
    method.setAccessible(true);
    String result = (String) method.invoke(vsacService, code);
    assertNull(result);
  }
}
