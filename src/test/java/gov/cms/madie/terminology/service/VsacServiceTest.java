package gov.cms.madie.terminology.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.commons.lang.Collections;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.models.cql.terminology.VsacCode.VsacError;
import gov.cms.madie.models.mapping.CodeSystemEntry;

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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
  private static final String TEST_TGT = "te$tTgt";

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
            .oid("1.2.3.4.5.6.7.8.9")
            .url("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .versions(Collections.toList(version))
            .build();
    codeSystemEntries.add(codeSystemEntry);

    File file = TestHelpers.getTestResourceFile("/value-sets/svs_office_visit.xml");
    JAXBContext jaxbContext = JAXBContext.newInstance(RetrieveMultipleValueSetsResponse.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    svsValueSet = (RetrieveMultipleValueSetsResponse) jaxbUnmarshaller.unmarshal(file);
    ValueSetsSearchCriteria.ValueSetParams valueSetParams =
        new ValueSetsSearchCriteria.ValueSetParams();
    valueSetParams.setOid("2.16.840.1.113883.3.464.1003.101.12.1001");
    valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .profile("eCQM Update 2030-05-05")
            .valueSetParams(List.of(valueSetParams))
            .build();

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).tgt(TEST_TGT).build();
  }

  @Test
  void testAValidCodeFromVsac() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/ActPriority/Version/HL7V3.0_2021-03/Code/P/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
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
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
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
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
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
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertFalse(result.get(0).isValid());
    assertEquals("Code not found", result.get(0).getErrorMessage());
  }

  @Test
  void testIfCqlCodesListIsEmpty() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(new ArrayList<>(), umlsUser);
    assertEquals(0, result.size());
  }

  @Test
  void testIfCqlCodeDoesNotContainOid() {
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    cqlCodes.get(0).getCodeSystem().setOid(null);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Code system URL is required", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfThereIsNoAssociatedCodeSystemEntry() {
    codeSystemEntries.get(0).setUrl("test-Url");
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals("Invalid Code system", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemIsNotInVsac() {
    codeSystemEntries.get(0).setOid("NOT.IN.VSAC");
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertTrue(result.get(0).isValid());
  }

  @Test
  void testIfCodeIdIsNotProvided() {
    cqlCodes.get(0).setCodeId(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertFalse(result.get(0).isValid());
    assertEquals("Code Id is required", result.get(0).getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsNotProvided() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    codeSystemEntries.get(0).setVersions(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryHasAKnownVersionButTheVsacValueIsNull() {
    cqlCodes.get(0).getCodeSystem().setVersion(null);
    codeSystemEntries.get(0).getVersions().get(0).setVsac(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertFalse(result.get(0).getCodeSystem().isValid());
    assertEquals(
        "Unable to find a code system version", result.get(0).getCodeSystem().getErrorMessage());
  }

  @Test
  void testIfCodeSystemEntryDoesNotHaveAnyKnownVersionsWhenCqlCodeSystemVersionIsProvided() {
    codeSystemEntries.get(0).setVersions(null);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    when(terminologyServiceWebClient.getCode(anyString(), anyString())).thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(cqlCodes, umlsUser);
    assertTrue(result.get(0).isValid());
  }

  @Test
  public void testGetValueSets() {
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("ST-fake");
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
  public void testGetValueSetsWhenErrorOccurredWhileFetchingServiceTicket() {
    when(terminologyServiceWebClient.getServiceTicket(any())).thenReturn(null);
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));

    VsacUnauthorizedException exception =
        assertThrows(
            VsacUnauthorizedException.class,
            () -> vsacService.getValueSets(valueSetsSearchCriteria, umlsUser));

    assertThat(
        exception.getMessage(),
        is(equalTo("Error occurred while fetching service ticket. Please contact helpdesk.")));
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
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn("Service-Ticket");
    when(terminologyServiceWebClient.getCode(
            eq("/CodeSystem/SNOMEDCT/Version/2022-03/Code/37687000/Info"), anyString()))
        .thenReturn(vsacCode);
    List<CqlCode> result = vsacService.validateCodes(List.of(snomedCode), umlsUser);
    assertTrue(result.get(0).isValid());
  }

  @Test
  public void testSaveUmlsUser() {
    ArgumentCaptor<UmlsUser> captor = ArgumentCaptor.forClass(UmlsUser.class);
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));
    UmlsUser saved = vsacService.saveUmlsUser(TEST_API_KEY, TEST_HARP_ID, TEST_TGT);
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
  public void testGetTGT() throws InterruptedException, ExecutionException {
    when(terminologyServiceWebClient.getTgt(anyString())).thenReturn(TEST_TGT);
    String result = vsacService.getTgt(TEST_API_KEY);
    assertEquals(TEST_TGT, result);
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
  public void testValidateUmlsInformationWhenTgtIsNotAvailable()
      throws ExecutionException, InterruptedException {
    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);

    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(optionalUmlsUser);
    when(optionalUmlsUser.get().getTgt()).thenReturn(null);
    when(optionalUmlsUser.get().getApiKey()).thenReturn(TEST_API_KEY);
    when(terminologyServiceWebClient.getTgt(anyString())).thenReturn(TEST_TGT);

    ArgumentCaptor<UmlsUser> captor = ArgumentCaptor.forClass(UmlsUser.class);
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));
    boolean isValidUmlsUserInfo = vsacService.validateUmlsInformation("test_user");
    verify(umlsUserRepository, times(1)).save(captor.capture());
    UmlsUser captored = captor.getValue();
    assertNotNull(captored);

    assertTrue(isValidUmlsUserInfo);
  }

  @Test
  public void testValidateUmlsInformationWhenTgtIsNotExpired() {
    Optional<UmlsUser> optionalUmlsUser = Optional.of(umlsUser);
    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(optionalUmlsUser);
    when(terminologyServiceWebClient.getServiceTicket(anyString()))
        .thenReturn("test_service_ticket");

    assertTrue(vsacService.validateUmlsInformation("test_user"));
  }

  @Test
  public void testValidateUmlsInformationWhenTgtIsExpired()
      throws ExecutionException, InterruptedException {
    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);

    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(optionalUmlsUser);
    when(optionalUmlsUser.get().getApiKey()).thenReturn(TEST_API_KEY);

    when(optionalUmlsUser.get().getTgt()).thenReturn(TEST_TGT);
    when(terminologyServiceWebClient.getServiceTicket(TEST_TGT)).thenReturn(null); // tgt expired

    when(terminologyServiceWebClient.getTgt(anyString())).thenReturn("new_tgt"); // fetching new tgt
    when(terminologyServiceWebClient.getServiceTicket("new_tgt")).thenReturn("test_service_ticket");

    ArgumentCaptor<UmlsUser> captor = ArgumentCaptor.forClass(UmlsUser.class);
    umlsUser.setTgt("new_tgt");
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));
    assertTrue(vsacService.validateUmlsInformation("test_user"));
    verify(umlsUserRepository, times(1)).save(captor.capture());
    UmlsUser captored = captor.getValue();
    assertNotNull(captored);
  }

  @Test
  public void testValidateUmlsInformationWhenTgtIsExpiredAndUnableToGenerateNewServiceTicket()
      throws ExecutionException, InterruptedException {
    Optional<UmlsUser> optionalUmlsUser = Optional.of(umlsUser);

    when(umlsUserRepository.findByHarpId(anyString())).thenReturn(optionalUmlsUser);
    when(terminologyServiceWebClient.getServiceTicket(anyString())).thenReturn(null);

    when(terminologyServiceWebClient.getTgt(anyString())).thenReturn("new_tgt"); // fetching new tgt

    ArgumentCaptor<UmlsUser> captor = ArgumentCaptor.forClass(UmlsUser.class);
    doReturn(umlsUser).when(umlsUserRepository).save(any(UmlsUser.class));
    assertFalse(vsacService.validateUmlsInformation("test_user"));
    verify(umlsUserRepository, times(3)).save(captor.capture());
    UmlsUser captored = captor.getValue();
    assertNotNull(captored);
  }
}
