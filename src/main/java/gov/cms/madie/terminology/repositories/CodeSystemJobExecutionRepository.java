package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CodeSystemJobExecutionRepository
    extends MongoRepository<CodeSystemJobExecutionState, String> {

  List<CodeSystemJobExecutionState> findByStatusAndStartedAtBefore(
      CodeSystemJobExecutionState.Status status, Instant startedAt);

  boolean existsByStatus(CodeSystemJobExecutionState.Status status);

  Optional<CodeSystemJobExecutionState> findFirstByStatusOrderByCompletedAtDesc(
      CodeSystemJobExecutionState.Status status);

  Optional<CodeSystemJobExecutionState> findFirstByStatusInOrderByCompletedAtDesc(
      Collection<CodeSystemJobExecutionState.Status> statuses);
}
