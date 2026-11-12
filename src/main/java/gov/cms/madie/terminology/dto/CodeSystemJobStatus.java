package gov.cms.madie.terminology.dto;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.Status;

import java.time.Instant;

public record CodeSystemJobStatus(Status status, Instant completedAt) {

  public static CodeSystemJobStatus from(CodeSystemJobExecutionState execution) {
    return new CodeSystemJobStatus(execution.getStatus(), execution.getCompletedAt());
  }
}
