package gov.cms.madie.terminology.dto;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.CodeSystemFailure;
import java.util.List;

public record CodeSystemRefreshResult(
    List<CodeSystem> updatedCodeSystems,
    List<CodeSystemFailure> failures,
    List<CodeSystemFailure> skipped) {
  public CodeSystemRefreshResult {
    updatedCodeSystems = List.copyOf(updatedCodeSystems);
    failures = List.copyOf(failures);
    skipped = List.copyOf(skipped);
  }
}
