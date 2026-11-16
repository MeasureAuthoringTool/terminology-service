package gov.cms.madie.terminology.task;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.dto.CodeSystemRefreshResult;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.Status;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemJobExecutionRepository;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.service.CodeSystemJobExecutionService;
import gov.cms.madie.terminology.service.CodeSystemService;
import gov.cms.madie.terminology.service.CodeSystemSyncService;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemAsyncWorkerTest {

  @Mock private CodeSystemSyncService codeSystemSyncService;
  @Mock private CodeSystemJobExecutionService codeSystemJobExecutionService;

  @InjectMocks private CodeSystemAsyncWorker codeSystemAsyncWorker;

  private static final String API_KEY = "abc-test";

  private final CodeSystemJobExecutionState execution =
      CodeSystemJobExecutionState.builder().id("job-1").build();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(codeSystemAsyncWorker, "apiKey", API_KEY);
    lenient()
        .when(codeSystemSyncService.remoteRetrieveAllCodeSystems(any(UmlsUser.class)))
        .thenReturn(new CodeSystemRefreshResult(List.of(), List.of(), List.of()));
  }

  @Test
  void testRunJobAsyncRunsSuccessfully() {
    // given
    AtomicBoolean running = new AtomicBoolean(true);
    CodeSystemRefreshResult result = new CodeSystemRefreshResult(List.of(), List.of(), List.of());
    when(codeSystemSyncService.remoteRetrieveAllCodeSystems(any(UmlsUser.class)))
        .thenReturn(result);

    // when
    codeSystemAsyncWorker.runJobAsync(running, execution);

    // then
    verify(codeSystemSyncService, times(1)).remoteRetrieveAllCodeSystems(any(UmlsUser.class));
    verify(codeSystemJobExecutionService, times(1)).finish(execution, result);
    verify(codeSystemJobExecutionService, never()).fail(any(), any());
    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncCallsServiceWithApiKeySet() {
    // given
    AtomicBoolean running = new AtomicBoolean(true);

    // when
    codeSystemAsyncWorker.runJobAsync(running, execution);

    // then
    verify(codeSystemSyncService)
        .remoteRetrieveAllCodeSystems(
            argThat(user -> user != null && API_KEY.equals(user.getApiKey())));
  }

  @Test
  void testRunJobAsyncHandlesExceptionAndResetsRunningFlag() {
    // given
    AtomicBoolean running = new AtomicBoolean(true);
    IllegalStateException failure = new IllegalStateException("VSAC unavailable");

    doThrow(failure).when(codeSystemSyncService).remoteRetrieveAllCodeSystems(any(UmlsUser.class));

    // when
    codeSystemAsyncWorker.runJobAsync(running, execution);

    // then
    verify(codeSystemJobExecutionService, times(1)).fail(execution, failure);
    verify(codeSystemJobExecutionService, never())
        .finish(any(), any(CodeSystemRefreshResult.class));
    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncFailsJobWhenRecordingCompletionFails() {
    // given
    AtomicBoolean running = new AtomicBoolean(true);
    RuntimeException failure = new RuntimeException("Mongo is down");

    doThrow(failure)
        .when(codeSystemJobExecutionService)
        .finish(eq(execution), any(CodeSystemRefreshResult.class));

    // when
    codeSystemAsyncWorker.runJobAsync(running, execution);

    // then
    verify(codeSystemJobExecutionService, times(1)).fail(execution, failure);
    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncResetsRunningFlagOnError() {
    AtomicBoolean running = new AtomicBoolean(true);

    doThrow(new StackOverflowError())
        .when(codeSystemSyncService)
        .remoteRetrieveAllCodeSystems(any(UmlsUser.class));

    assertThrows(
        StackOverflowError.class, () -> codeSystemAsyncWorker.runJobAsync(running, execution));

    verifyNoInteractions(codeSystemJobExecutionService);
    assertFalse(running.get());
  }

  @Test
  void refreshPersistsPartialJobThenSeparateSuccessfulJobWithoutLosingHistory() {
    // given
    FhirContext context = FhirContext.forR4();
    var parser = context.newJsonParser();
    var webClient = mock(FhirTerminologyServiceWebClient.class);
    var codeSystemRepository = mock(CodeSystemRepository.class);
    var codeSystemService = mock(CodeSystemService.class);
    when(codeSystemService.createCodeSystem(any(CodeSystem.class)))
        .thenAnswer(
            invocation -> {
              CodeSystem codeSystem = invocation.getArgument(0);
              codeSystem.setId("stored-" + codeSystem.getFullUrl());
              return codeSystem;
            });
    var jobRepository = mock(CodeSystemJobExecutionRepository.class);
    Bundle search = new Bundle();
    for (String id : List.of("failed", "updated")) {
      var codeSystem = new org.hl7.fhir.r4.model.CodeSystem();
      codeSystem.setId(id);
      codeSystem.setUrl("http://example.com/" + id);
      codeSystem.setVersion("2");
      search.addEntry().setResource(codeSystem);
    }
    when(webClient.getCodeSystemsPage(0, 50, API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(webClient.getCodeSystemHistoryPage("failed", 0, 50, API_KEY))
        .thenThrow(new IllegalStateException("VSAC unavailable"))
        .thenReturn("{\"resourceType\":\"Bundle\",\"type\":\"history\"}");
    when(webClient.getCodeSystemHistoryPage("updated", 0, 50, API_KEY))
        .thenReturn("{\"resourceType\":\"Bundle\",\"type\":\"history\"}");
    List<CodeSystemJobExecutionState> saved = new ArrayList<>();
    when(jobRepository.save(any(CodeSystemJobExecutionState.class)))
        .thenAnswer(
            invocation -> {
              CodeSystemJobExecutionState job = invocation.getArgument(0);
              if (job.getId() == null) {
                job.setId("job-" + saved.size());
              }
              saved.add(job.toBuilder().build());
              return job;
            });
    var jobService = new CodeSystemJobExecutionService(jobRepository);
    var syncService =
        new CodeSystemSyncService(context, webClient, codeSystemService, codeSystemRepository);
    var worker = new CodeSystemAsyncWorker(syncService, jobService);
    ReflectionTestUtils.setField(worker, "apiKey", API_KEY);
    var task = new UpdateCodeSystemTask(worker, jobService);

    // when
    boolean firstStarted = task.startJob("test-harp-id");
    boolean secondStarted = task.startJob("test-harp-id");

    // then
    assertTrue(firstStarted);
    assertTrue(secondStarted);
    assertFalse(task.isRunning());
    assertEquals(
        List.of(Status.RUNNING, Status.COMPLETED_WITH_ERRORS, Status.RUNNING, Status.COMPLETED),
        saved.stream().map(CodeSystemJobExecutionState::getStatus).toList());
    assertEquals(saved.get(0).getId(), saved.get(1).getId());
    assertEquals(saved.get(2).getId(), saved.get(3).getId());
    assertNotEquals(saved.get(1).getId(), saved.get(3).getId());
    CodeSystemJobExecutionState partial = saved.get(1);
    assertEquals("test-harp-id", partial.getTriggeredBy());
    assertEquals("failed", partial.getFailures().get(0).codeSystemId());
    assertEquals("http://example.com/failed", partial.getFailures().get(0).fullUrl());
    assertEquals("HISTORY", partial.getFailures().get(0).stage());
    assertTrue(partial.getFailures().get(0).message().contains("VSAC unavailable"));
    assertEquals(
        List.of("http://example.com/updated"),
        partial.getSuccessfulCodeSystems().stream().map(update -> update.fullUrl()).toList());
    assertEquals(
        Duration.between(partial.getStartedAt(), partial.getCompletedAt()).toMillis(),
        partial.getDurationMs());
    assertTrue(saved.get(3).getFailures().isEmpty());
    assertNull(saved.get(3).getFailureMessage());
    assertEquals(2, saved.get(3).getSuccessfulCodeSystems().size());
    verify(codeSystemService, times(2))
        .createCodeSystem(argThat(cs -> "http://example.com/updated".equals(cs.getFullUrl())));
    verify(codeSystemService)
        .createCodeSystem(argThat(cs -> "http://example.com/failed".equals(cs.getFullUrl())));
  }
}
