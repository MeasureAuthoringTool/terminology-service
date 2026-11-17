package gov.cms.madie.terminology.dto;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.Status;

import java.time.Instant;
import java.util.List;

/** Admin view of a code system update job. Counts are null while running or when not recorded */
public record CodeSystemJobAdminStatus(
    Status status,
    Instant completedAt,
    Integer successCount,
    Integer failureCount,
    Integer skippedCount) {

  public static CodeSystemJobAdminStatus running() {
    return new CodeSystemJobAdminStatus(Status.RUNNING, null, null, null, null);
  }

  public static CodeSystemJobAdminStatus from(CodeSystemJobExecutionState execution) {
    return new CodeSystemJobAdminStatus(
        execution.getStatus(),
        execution.getCompletedAt(),
        count(execution.getSuccessfulCodeSystems()),
        count(execution.getFailures()),
        count(execution.getSkippedCodeSystems()));
  }

  private static Integer count(List<?> items) {
    return items == null ? null : items.size();
  }
}
