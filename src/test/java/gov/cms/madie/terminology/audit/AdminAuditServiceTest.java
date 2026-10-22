package gov.cms.madie.terminology.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.terminology.audit.AdminAuditService.Action;
import gov.cms.madie.terminology.audit.AuditResource.ResourceType;
import gov.cms.madie.terminology.clients.UserServiceClient;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

  private static final String HARP_ID = "jdoe123";
  private static final Pattern TIMESTAMP = Pattern.compile("timestamp=(\\S+)");

  @Mock private UserServiceClient userServiceClient;
  @InjectMocks private AdminAuditService adminAuditService;

  private Principal principal;
  private Logger auditLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    principal = mock(Principal.class);
    when(principal.getName()).thenReturn(HARP_ID);

    auditLogger = (Logger) LoggerFactory.getLogger("action_audit");
    appender = new ListAppender<>();
    appender.start();
    auditLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    auditLogger.detachAppender(appender);
  }

  @Test
  void logsCodeSystemActionWithAllFields() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails("Jane", "Doe"));
    CodeSystem codeSystem =
        CodeSystem.builder()
            .id("LOINCversion2.40")
            .title("LOINC")
            .name("LOINC")
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("http://loinc.org")
            .version(CodeSystem.Version.builder().fhirVersion("2.40").vsacVersion("2.40").build())
            .build();

    adminAuditService.logAdminAction(principal, Action.UPDATE, AuditResource.from(codeSystem));

    assertEquals(
        "ADMIN_AUDIT timestamp=<ts> action=UPDATE resourceType=CODE_SYSTEM"
            + " resourceId=\"LOINCversion2.40\" title=\"LOINC\" name=\"LOINC\""
            + " oid=\"urn:oid:2.16.840.1.113883.6.1\" fullUrl=\"http://loinc.org\""
            + " fhirVersion=\"2.40\" vsacVersion=\"2.40\" harpId=\"jdoe123\""
            + " adminName=\"Jane Doe\" adminPrivileges=true",
        withoutTimestamp(singleAuditLine()));
  }

  @Test
  void logsValueSetActionWithAllFields() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails("Jane", "Doe"));
    MadieValueSet valueSet =
        MadieValueSet.builder()
            .id("vs-1")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
            .version("20240101")
            .oid("1.2.3")
            .displayName("Office Visit")
            .valueSet("{}")
            .build();

    adminAuditService.logAdminAction(principal, Action.DELETE, AuditResource.from(valueSet));

    assertEquals(
        "ADMIN_AUDIT timestamp=<ts> action=DELETE resourceType=VALUE_SET resourceId=\"vs-1\""
            + " url=\"http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3\" version=\"20240101\""
            + " oid=\"1.2.3\" displayName=\"Office Visit\" harpId=\"jdoe123\""
            + " adminName=\"Jane Doe\" adminPrivileges=true",
        withoutTimestamp(singleAuditLine()));
  }

  @Test
  void logsBulkActionWithDescription() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails("Jane", "Doe"));

    adminAuditService.logAdminAction(
        principal,
        Action.UPDATE,
        AuditResource.bulk(
            ResourceType.CODE_SYSTEM,
            "Update Code Systems: started job to refresh all code systems from VSAC"));

    assertEquals(
        "ADMIN_AUDIT timestamp=<ts> action=UPDATE resourceType=CODE_SYSTEM resourceId=\"ALL\""
            + " description=\"Update Code Systems: started job to refresh all code systems"
            + " from VSAC\" harpId=\"jdoe123\""
            + " adminName=\"Jane Doe\" adminPrivileges=true",
        withoutTimestamp(singleAuditLine()));
  }

  @Test
  void timestampIsIso8601Instant() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails("Jane", "Doe"));
    Instant before = Instant.now();

    adminAuditService.logAdminAction(
        principal, Action.CREATE, AuditResource.bulk(ResourceType.VALUE_SET, "test"));

    Matcher matcher = TIMESTAMP.matcher(singleAuditLine());
    assertTrue(matcher.find());
    Instant logged = Instant.parse(matcher.group(1));
    assertFalse(logged.isBefore(before));
    assertFalse(logged.isAfter(Instant.now()));
  }

  @Test
  void usesUnknownAndWarnsWhenUserDetailsLookupFails() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(null);

    adminAuditService.logAdminAction(
        principal, Action.CREATE, AuditResource.bulk(ResourceType.VALUE_SET, "test"));

    assertTrue(singleAuditLine().contains(" adminName=\"UNKNOWN\" "));
    assertTrue(
        eventsAt(Level.WARN).stream().anyMatch(m -> m.contains("Could not look up user details")));
  }

  @Test
  void usesUnknownAndWarnsWhenUserHasNoName() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails(" ", null));

    adminAuditService.logAdminAction(
        principal, Action.CREATE, AuditResource.bulk(ResourceType.VALUE_SET, "test"));

    assertTrue(singleAuditLine().contains(" adminName=\"UNKNOWN\" "));
    assertTrue(eventsAt(Level.WARN).stream().anyMatch(m -> m.contains("no first or last name")));
  }

  @Test
  void usesWhicheverNamePartIsPresent() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails(" Jane ", ""));

    adminAuditService.logAdminAction(
        principal, Action.CREATE, AuditResource.bulk(ResourceType.VALUE_SET, "test"));

    assertTrue(singleAuditLine().contains(" adminName=\"Jane\" "));
  }

  @Test
  void sanitizesQuotesAndLineBreaksInValues() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenReturn(userDetails("Jane", "Doe"));
    CodeSystem codeSystem =
        CodeSystem.builder().id("cs-1").name("LOINC \"v2\"\r\nADMIN_AUDIT forged").build();

    adminAuditService.logAdminAction(principal, Action.UPDATE, AuditResource.from(codeSystem));

    String line = singleAuditLine();
    assertTrue(line.contains(" name=\"LOINC 'v2' ADMIN_AUDIT forged\" "));
    assertFalse(line.contains("\n"));
    assertFalse(line.contains("\r"));
  }

  @Test
  void neverThrowsWhenAuditingFails() {
    when(userServiceClient.getUserDetails(HARP_ID)).thenThrow(new RuntimeException("boom"));

    assertDoesNotThrow(
        () ->
            adminAuditService.logAdminAction(
                principal, Action.CREATE, AuditResource.bulk(ResourceType.VALUE_SET, "test")));
    assertTrue(eventsAt(Level.INFO).isEmpty());
    assertTrue(
        eventsAt(Level.ERROR).stream()
            .anyMatch(m -> m.contains("Failed to write admin audit log for action [CREATE]")));
  }

  private String singleAuditLine() {
    List<String> lines = eventsAt(Level.INFO);
    assertEquals(1, lines.size());
    return lines.get(0);
  }

  private List<String> eventsAt(Level level) {
    return appender.list.stream()
        .filter(event -> event.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private static String withoutTimestamp(String line) {
    return TIMESTAMP.matcher(line).replaceFirst("timestamp=<ts>");
  }

  private static UserDetailsDto userDetails(String firstName, String lastName) {
    return UserDetailsDto.builder().harpId(HARP_ID).firstName(firstName).lastName(lastName).build();
  }
}
