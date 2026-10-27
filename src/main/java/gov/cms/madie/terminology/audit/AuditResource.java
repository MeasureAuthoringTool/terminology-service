package gov.cms.madie.terminology.audit;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The resource affected by an audited admin action. Each attribute is written to the audit log as
 * its own key=value field so it can be searched
 */
public record AuditResource(ResourceType type, String id, Map<String, String> attributes) {

  public static final String ALL = "ALL";

  public enum ResourceType {
    CODE_SYSTEM,
    VALUE_SET
  }

  public static AuditResource from(CodeSystem codeSystem) {
    Map<String, String> attributes = new LinkedHashMap<>();
    putIfPresent(attributes, "title", codeSystem.getTitle());
    putIfPresent(attributes, "name", codeSystem.getName());
    putIfPresent(attributes, "oid", codeSystem.getOid());
    putIfPresent(attributes, "fullUrl", codeSystem.getFullUrl());
    if (codeSystem.getVersion() != null) {
      putIfPresent(attributes, "fhirVersion", codeSystem.getVersion().getFhirVersion());
      putIfPresent(attributes, "vsacVersion", codeSystem.getVersion().getVsacVersion());
    }
    return new AuditResource(ResourceType.CODE_SYSTEM, codeSystem.getId(), attributes);
  }

  public static AuditResource from(MadieValueSet valueSet) {
    Map<String, String> attributes = new LinkedHashMap<>();
    putIfPresent(attributes, "url", valueSet.getUrl());
    putIfPresent(attributes, "version", valueSet.getVersion());
    putIfPresent(attributes, "oid", valueSet.getOid());
    putIfPresent(attributes, "displayName", valueSet.getDisplayName());
    return new AuditResource(ResourceType.VALUE_SET, valueSet.getId(), attributes);
  }

  /**
   * A bulk action, such as a refresh job started from an admin button, that affects every resource
   * of a type. The description says in plain words what was triggered.
   */
  public static AuditResource bulk(ResourceType type, String description) {
    return new AuditResource(type, ALL, Map.of("description", description));
  }

  private static void putIfPresent(Map<String, String> attributes, String key, String value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
