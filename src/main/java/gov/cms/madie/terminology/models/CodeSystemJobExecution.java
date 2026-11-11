package gov.cms.madie.terminology.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Document
public class CodeSystemJobExecution {
  @Id private String id;
  private Status status;
  private String triggeredBy; // HARP ID, or SYSTEM for scheduled runs
  private Instant startedAt;
  private Instant completedAt;
  private Long durationMs;

  public enum Status {
    RUNNING,
    COMPLETED,
    FAILED
  }
}
