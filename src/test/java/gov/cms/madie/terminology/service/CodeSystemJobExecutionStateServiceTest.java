package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.dto.CodeSystemJobStatus;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.Status;
import gov.cms.madie.terminology.repositories.CodeSystemJobExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    when(codeSystemJobExecutionRepository.save(any(CodeSystemJobExecutionState.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Instant before = Instant.now();

    CodeSystemJobExecutionState execution = codeSystemJobExecutionService.start(HARP_ID);

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
    assertSame(saved, execution);
  }

  @Test
  void testCompleteUpdatesRecordWithCompletionTimeAndDuration() {
    Instant startedAt = Instant.now().minusSeconds(90);
    CodeSystemJobExecutionState execution = runningExecution(startedAt);

    codeSystemJobExecutionService.complete(execution);

    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals("job-1", execution.getId());
    assertEquals(Status.COMPLETED, execution.getStatus());
    assertEquals(startedAt, execution.getStartedAt());
    assertNotNull(execution.getCompletedAt());
    assertFalse(execution.getCompletedAt().isBefore(startedAt));
    assertEquals(
        Duration.between(startedAt, execution.getCompletedAt()).toMillis(),
        execution.getDurationMs());
    assertTrue(execution.getDurationMs() >= 90_000);
  }

  @Test
  void testFailUpdatesRecordWithCompletionTimeAndDuration() {
    Instant startedAt = Instant.now().minusSeconds(30);
    CodeSystemJobExecutionState execution = runningExecution(startedAt);

    codeSystemJobExecutionService.fail(execution);

    verify(codeSystemJobExecutionRepository).save(execution);
    assertEquals(Status.FAILED, execution.getStatus());
    assertNotNull(execution.getCompletedAt());
    assertEquals(
        Duration.between(startedAt, execution.getCompletedAt()).toMillis(),
        execution.getDurationMs());
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testIsJobRunningReflectsRunningRecordInDatabase(boolean running) {
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(running);

    assertEquals(running, codeSystemJobExecutionService.isJobRunning());
  }

  @Test
  void testGetLatestJobStatusReturnsRunningWhenAJobIsRunning() {
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(true);

    Optional<CodeSystemJobStatus> jobStatus = codeSystemJobExecutionService.getLatestJobStatus();

    assertEquals(new CodeSystemJobStatus(Status.RUNNING, null), jobStatus.orElseThrow());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"COMPLETED", "FAILED"})
  void testGetLatestJobStatusReturnsMostRecentlyFinishedJob(Status status) {
    Instant completedAt = Instant.now();
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(false);
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(
            List.of(Status.COMPLETED, Status.FAILED)))
        .thenReturn(Optional.of(finishedExecution(status, completedAt)));

    Optional<CodeSystemJobStatus> jobStatus = codeSystemJobExecutionService.getLatestJobStatus();

    assertEquals(new CodeSystemJobStatus(status, completedAt), jobStatus.orElseThrow());
  }

  @Test
  void testGetLatestJobStatusIsEmptyWhenNoJobHasRun() {
    when(codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)).thenReturn(false);
    when(codeSystemJobExecutionRepository.findFirstByStatusInOrderByCompletedAtDesc(any()))
        .thenReturn(Optional.empty());

    assertTrue(codeSystemJobExecutionService.getLatestJobStatus().isEmpty());
  }

  @Test
  void testGetLastCompletedJobReturnsMostRecentCompletedJob() {
    Instant completedAt = Instant.now();
    when(codeSystemJobExecutionRepository.findFirstByStatusOrderByCompletedAtDesc(Status.COMPLETED))
        .thenReturn(Optional.of(finishedExecution(Status.COMPLETED, completedAt)));

    Optional<CodeSystemJobStatus> jobStatus = codeSystemJobExecutionService.getLastCompletedJob();

    assertEquals(new CodeSystemJobStatus(Status.COMPLETED, completedAt), jobStatus.orElseThrow());
  }

  @Test
  void testGetLastCompletedJobIsEmptyWhenNoJobHasCompleted() {
    when(codeSystemJobExecutionRepository.findFirstByStatusOrderByCompletedAtDesc(Status.COMPLETED))
        .thenReturn(Optional.empty());

    assertTrue(codeSystemJobExecutionService.getLastCompletedJob().isEmpty());
  }
}
