package gov.cms.madie.terminology.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Document
public class CodeSystemJobExecutionState {
  @Id private String id;
  private Status status;
  private String triggeredBy; // HARP ID, or SYSTEM for scheduled runs
  private Instant startedAt;
  private Instant completedAt;
  private Long durationMs;
  private List<CodeSystemFailure> failures;
  private List<CodeSystemUpdate> successfulCodeSystems;
  // Invalid upstream entries (e.g. missing version), kept separate from operational failures
  private List<CodeSystemFailure> skippedCodeSystems;
  private String failureMessage;

  public record CodeSystemUpdate(String id, String fullUrl, String version) {}

  public record CodeSystemFailure(
      String codeSystemId, String fullUrl, String version, String stage, String message) {}

  public enum Status {
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_FAILURES,
    FAILED
  }
}
