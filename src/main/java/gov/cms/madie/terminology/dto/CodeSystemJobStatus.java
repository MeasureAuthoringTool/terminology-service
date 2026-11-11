package gov.cms.madie.terminology.dto;

import gov.cms.madie.terminology.models.CodeSystemJobExecution;
import gov.cms.madie.terminology.models.CodeSystemJobExecution.Status;

import java.time.Instant;

public record CodeSystemJobStatus(Status status, Instant completedAt) {

  public static CodeSystemJobStatus from(CodeSystemJobExecution execution) {
    return new CodeSystemJobStatus(execution.getStatus(), execution.getCompletedAt());
  }
}
