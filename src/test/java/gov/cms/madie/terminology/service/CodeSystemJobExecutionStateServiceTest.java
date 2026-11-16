package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.dto.CodeSystemJobAdminStatus;
import gov.cms.madie.terminology.dto.CodeSystemJobStatus;
import gov.cms.madie.terminology.dto.CodeSystemRefreshResult;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.CodeSystemFailure;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.CodeSystemUpdate;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.Status;
import gov.cms.madie.terminology.repositories.CodeSystemJobExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemJobExecutionStateServiceTest {

  private static final String HARP_ID = "test-harp-id";

  @Mock private CodeSystemJobExecutionRepository codeSystemJobExecutionRepository;

  @InjectMocks private CodeSystemJobExecutionService codeSystemJobExecutionService;

  private CodeSystemJobExecutionState runningExecution(Instant startedAt) {
    return CodeSystemJobExecutionState.builder()
        .id("job-1")
        .status(Status.RUNNING)
        .triggeredBy(HARP_ID)
        .startedAt(startedAt)
        .build();
  }

  private CodeSystemJobExecutionState finishedExecution(Status status, Instant completedAt) {
    return CodeSystemJobExecutionState.builder().status(status).completedAt(completedAt).build();
  }

  @Test
  void testStartSavesNewRunningRecord() {
    // given
    when(codeSystemJobExecutionRepository.save(any(CodeSystemJobExecutionState.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Instant before = Instant.now();

    // when
    CodeSystemJobExecutionState execution = codeSystemJobExecutionService.start(HARP_ID);

    // then
    ArgumentCaptor<CodeSystemJobExecutionState> captor =
        ArgumentCaptor.forClass(CodeSystemJobExecutionState.class);
    verify(codeSystemJobExecutionRepository).save(captor.capture());
    CodeSystemJobExecutionState saved = captor.getValue();
    assertNull(saved.getId());
    assertEquals(Status.RUNNING, saved.getStatus());
    assertEquals(HARP_ID, saved.getTriggeredBy());
    assertNotNull(saved.getStartedAt());
    assertFalse(saved.getStartedAt().isBefore(before));
    assertNull(saved.getCompletedAt());
    assertNull(saved.getDurationMs());
    assertEquals(List.of(), saved.getFailures());
    assertTrue(saved.getSuccessfulCodeSystems().isEmpty());
    assertTrue(saved.getSkippedCodeSystems().isEmpty());
    assertSame(saved, execution);
  }

  @Test
  void testCompleteUpdatesRecordWithCompletionTimeAndDuration() {
    // given
    Instant startedAt = Instant.now().minusSeconds(90);
    CodeSystemJobExecutionState execution = runningExecution(startedAt);
    List<CodeSystemFailure> skipped =
        List.of(
            new CodeSystemFailure(
                null,
                "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl",
                null,
                "VALIDATION",
                "CodeSystem is missing a valid FHIR version"));
    CodeSystemRefreshResult result =
        new CodeSystemRefreshResult(
            List.of(
                CodeSystem.builder()
                    .id("stored-1")
                    .fullUrl("http://example.com/system")
                    .version(CodeSystem.Version.builder().fhirVersion("2").build())
                    .build()),
            List.of(),
            skipped);

    // when
    codeSystemJobExecutionService.finish(execution, result);

    // then
    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals("job-1", execution.getId());
    assertEquals(Status.COMPLETED, execution.getStatus());
    assertEquals(skipped, execution.getSkippedCodeSystems());
    assertEquals(startedAt, execution.getStartedAt());
    assertNotNull(execution.getCompletedAt());
    assertFalse(execution.getCompletedAt().isBefore(startedAt));
    assertEquals(
        Duration.between(startedAt, execution.getCompletedAt()).toMillis(),
        execution.getDurationMs());
    assertTrue(execution.getDurationMs() >= 90_000);
    assertEquals(
        List.of(new CodeSystemUpdate("stored-1", "http://example.com/system", "2")),
        execution.getSuccessfulCodeSystems());
    assertTrue(execution.getFailures().isEmpty());
    assertNull(execution.getFailureMessage());
  }

  @Test
  void testFailUpdatesRecordWithCompletionTimeAndDuration() {
    // given
    Instant startedAt = Instant.now().minusSeconds(30);
    CodeSystemJobExecutionState execution = runningExecution(startedAt);

    // when
    codeSystemJobExecutionService.fail(execution);

    // then
    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals(Status.FAILED, execution.getStatus());
    assertNotNull(execution.getCompletedAt());
    assertEquals(
        Duration.between(startedAt, execution.getCompletedAt()).toMillis(),
        execution.getDurationMs());
  }

  @Test
  void finishMarksCompletedWithErrorsWhenSomeCodeSystemsFail() {
    // given
    Instant startedAt = Instant.now().minusSeconds(30);
    CodeSystemJobExecutionState execution = runningExecution(startedAt);
    List<CodeSystemFailure> failures =
        List.of(
            new CodeSystemFailure(
                "LOINC", "http://loinc.org", "2.83", "HISTORY", "VSAC unavailable"),
            new CodeSystemFailure(
                null, "http://snomed.info/sct", "2026", "SAVE", "Mongo write failed"));
    CodeSystemRefreshResult result =
        new CodeSystemRefreshResult(
            List.of(
                CodeSystem.builder()
                    .id("stored-1")
                    .fullUrl("http://example.com/system")
                    .version(CodeSystem.Version.builder().fhirVersion("2").build())
                    .build()),
            failures,
            List.of());

    // when
    codeSystemJobExecutionService.finish(execution, result);

    // then
    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals("job-1", execution.getId());
    assertEquals(HARP_ID, execution.getTriggeredBy());
    assertEquals(startedAt, execution.getStartedAt());
    assertEquals(Status.COMPLETED_WITH_ERRORS, execution.getStatus());
    assertEquals(failures, execution.getFailures());
    assertEquals(
        List.of(new CodeSystemUpdate("stored-1", "http://example.com/system", "2")),
        execution.getSuccessfulCodeSystems());
    assertTrue(execution.getFailureMessage().contains("2 code system update failure(s)"));
    assertEquals(
        Duration.between(startedAt, execution.getCompletedAt()).toMillis(),
        execution.getDurationMs());
  }

  @Test
  void finishMarksFailedWhenCodeSystemsFailAndNoneSucceed() {
    // given
    CodeSystemJobExecutionState execution = runningExecution(Instant.now().minusSeconds(30));
    List<CodeSystemFailure> failures =
        List.of(
            new CodeSystemFailure(
                "LOINC", "http://loinc.org", "2.83", "HISTORY", "VSAC unavailable"));
    CodeSystemRefreshResult result = new CodeSystemRefreshResult(List.of(), failures, List.of());

    // when
    codeSystemJobExecutionService.finish(execution, result);

    // then
    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals(Status.FAILED, execution.getStatus());
    assertEquals(failures, execution.getFailures());
    assertTrue(execution.getSuccessfulCodeSystems().isEmpty());
    assertEquals("1 code system update failure(s)", execution.getFailureMessage());
  }

  @Test
  void failPersistsJobLevelErrorWithoutInventingCodeSystemFailures() {
    // given
    CodeSystemJobExecutionState execution = runningExecution(Instant.now().minusSeconds(30));
    IllegalStateException error = new IllegalStateException("Search request failed");

    // when
    codeSystemJobExecutionService.fail(execution, error);

    // then
    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals(Status.FAILED, execution.getStatus());
    assertEquals(error.toString(), execution.getFailureMessage());
    assertNotNull(execution.getCompletedAt());
  }

  @Test
  void testFailInterruptedJobsMarksRunningRecordsFailed() {
    CodeSystemJobExecutionState first = runningExecution(Instant.now().minusSeconds(600));
    CodeSystemJobExecutionState second =
        runningExecution(Instant.now().minusSeconds(60)).toBuilder().id("job-2").build();
    when(codeSystemJobExecutionRepository.findByStatusAndStartedAtBefore(
            eq(Status.RUNNING), any(Instant.class)))
        .thenReturn(List.of(first, second));

    codeSystemJobExecutionService.failInterruptedJobs();

    verify(codeSystemJobExecutionRepository).save(first);
    verify(codeSystemJobExecutionRepository).save(second);
    assertEquals(Status.FAILED, first.getStatus());
    assertEquals(Status.FAILED, second.getStatus());
    assertNotNull(first.getCompletedAt());
    assertNotNull(second.getCompletedAt());
  }

  @Test
  void testFailInterruptedJobsIgnoresJobsStartedAfterServiceStart() {
    when(codeSystemJobExecutionRepository.findByStatusAndStartedAtBefore(
            eq(Status.RUNNING), any(Instant.class)))
        .thenReturn(List.of());

    codeSystemJobExecutionService.failInterruptedJobs();

    Instant serviceStartedAt =
        (Instant) ReflectionTestUtils.getField(codeSystemJobExecutionService, "serviceStartedAt");
    verify(codeSystemJobExecutionRepository)
        .findByStatusAndStartedAtBefore(Status.RUNNING, serviceStartedAt);
  }

  @Test
  void testFailInterruptedJobsDoesNothingWhenNoneRunning() {
    when(codeSystemJobExecutionRepository.findByStatusAndStartedAtBefore(
            eq(Status.RUNNING), any(Instant.class)))
        .thenReturn(List.of());

    codeSystemJobExecutionService.failInterruptedJobs();

    verify(codeSystemJobExecutionRepository, never()).save(any());
  }

  @Test
  void testFailInterruptedJobsNeverThrows() {
    when(codeSystemJobExecutionRepository.findByStatusAndStartedAtBefore(
            eq(Status.RUNNING), any(Instant.class)))
        .thenThrow(new RuntimeException("Mongo is down"));

    assertDoesNotThrow(() -> codeSystemJobExecutionService.failInterruptedJobs());
  }

  @Test
  void testGetLatestJobStatusReturnsRunningWhenAJobIsRunning() {
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(true);

    Optional<CodeSystemJobAdminStatus> jobStatus =
        codeSystemJobExecutionService.getLatestJobStatus();

    assertEquals(CodeSystemJobAdminStatus.running(), jobStatus.orElseThrow());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED"})
  void testGetLatestJobStatusReturnsMostRecentlyFinishedJobWithCounts(Status status) {
    // given
    Instant completedAt = Instant.now();
    CodeSystemFailure problem = new CodeSystemFailure(null, "http://x", null, "SAVE", "error");
    CodeSystemJobExecutionState execution =
        finishedExecution(status, completedAt).toBuilder()
            .successfulCodeSystems(
                List.of(
                    new CodeSystemUpdate("1", "http://a", "1"),
                    new CodeSystemUpdate("2", "http://a", "2")))
            .failures(List.of(problem))
            .skippedCodeSystems(List.of(problem, problem, problem))
            .build();
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(false);
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(
            List.of(Status.COMPLETED, Status.COMPLETED_WITH_ERRORS, Status.FAILED)))
        .thenReturn(Optional.of(execution));

    // when
    Optional<CodeSystemJobAdminStatus> jobStatus =
        codeSystemJobExecutionService.getLatestJobStatus();

    // then
    assertEquals(
        new CodeSystemJobAdminStatus(status, completedAt, 2, 1, 3), jobStatus.orElseThrow());
  }

  @Test
  void testGetLatestJobStatusReturnsNullCountsForJobsWithoutRecordedDetails() {
    // given
    Instant completedAt = Instant.now();
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(false);
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(any()))
        .thenReturn(Optional.of(finishedExecution(Status.COMPLETED, completedAt)));

    // when
    Optional<CodeSystemJobAdminStatus> jobStatus =
        codeSystemJobExecutionService.getLatestJobStatus();

    // then
    assertEquals(
        new CodeSystemJobAdminStatus(Status.COMPLETED, completedAt, null, null, null),
        jobStatus.orElseThrow());
  }

  @Test
  void testGetLatestJobStatusIsEmptyWhenNoJobHasRun() {
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(false);
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(any()))
        .thenReturn(Optional.empty());

    assertTrue(codeSystemJobExecutionService.getLatestJobStatus().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"COMPLETED", "COMPLETED_WITH_ERRORS"})
  void testGetLastCompletedJobIncludesJobsCompletedWithErrors(Status status) {
    // given
    Instant completedAt = Instant.now();
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(
            List.of(Status.COMPLETED, Status.COMPLETED_WITH_ERRORS)))
        .thenReturn(Optional.of(finishedExecution(status, completedAt)));

    // when
    Optional<CodeSystemJobStatus> jobStatus = codeSystemJobExecutionService.getLastCompletedJob();

    // then
    assertEquals(new CodeSystemJobStatus(status, completedAt), jobStatus.orElseThrow());
  }

  @Test
  void testGetLastCompletedJobIsEmptyWhenNoJobHasCompleted() {
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(
            List.of(Status.COMPLETED, Status.COMPLETED_WITH_ERRORS)))
        .thenReturn(Optional.empty());

    assertTrue(codeSystemJobExecutionService.getLastCompletedJob().isEmpty());
  }
}
