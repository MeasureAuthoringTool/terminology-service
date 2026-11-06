package gov.cms.madie.terminology.audit;

import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.terminology.clients.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;

/**
 * Writes one audit line per privileged create/update/delete made through the admin tabs. Lines go
 * to the action_audit logger and start with ADMIN_AUDIT
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "action_audit")
public class AdminAuditService {

  private static final String UNKNOWN_ADMIN_NAME = "UNKNOWN";

  private final UserServiceClient userServiceClient;

  public enum Action {
    CREATE,
    UPDATE,
    DELETE
  }

  /**
   * Logs a successful admin action. Never throws, so a failure to audit can't fail the admin's
   * action.
   */
  public void logAdminAction(Principal principal, Action action, AuditResource resource) {
    try {
      String harpId = principal.getName();
      StringBuilder line =
          new StringBuilder("ADMIN_AUDIT")
              .append(" timestamp=")
              .append(Instant.now())
              .append(" action=")
              .append(action)
              .append(" resourceType=")
              .append(resource.type())
              .append(" resourceId=")
              .append(sanitizeAndQuote(resource.id()));
      resource
          .attributes()
          .forEach(
              (key, value) ->
                  line.append(' ').append(key).append('=').append(sanitizeAndQuote(value)));
      line.append(" harpId=")
          .append(sanitizeAndQuote(harpId))
          .append(" adminName=")
          .append(sanitizeAndQuote(lookupAdminName(harpId)))
          .append(" adminPrivileges=true");
      log.info("{}", line);
    } catch (Exception e) {
      log.error("Failed to write admin audit log for action [{}]: {}", action, e.getMessage(), e);
    }
  }

  private String lookupAdminName(String harpId) {
    UserDetailsDto userDetails = userServiceClient.getUserDetails(harpId);
    if (userDetails == null) {
      log.warn(
          "Could not look up user details for admin [{}], audit entry will use adminName={}",
          harpId,
          UNKNOWN_ADMIN_NAME);
      return UNKNOWN_ADMIN_NAME;
    }
    String name =
        StringUtils.normalizeSpace(
            StringUtils.joinWith(" ", userDetails.getFirstName(), userDetails.getLastName()));
    if (name.isEmpty()) {
      log.warn(
          "User details for admin [{}] have no first or last name, audit entry will use"
              + " adminName={}",
          harpId,
          UNKNOWN_ADMIN_NAME);
      return UNKNOWN_ADMIN_NAME;
    }
    return name;
  }

  /** Quotes a value and strips characters that could break or forge an audit line. */
  private static String sanitizeAndQuote(String value) {
    String sanitized = value == null ? "" : value.replace('"', '\'').replaceAll("[\\r\\n]+", " ");
    return "\"" + sanitized + "\"";
  }
}
