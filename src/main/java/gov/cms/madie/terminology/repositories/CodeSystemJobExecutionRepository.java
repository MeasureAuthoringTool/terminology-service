package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.CodeSystemJobExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CodeSystemJobExecutionRepository
    extends MongoRepository<CodeSystemJobExecution, String> {

  List<CodeSystemJobExecution> findByStatusAndStartedAtBefore(
      CodeSystemJobExecution.Status status, Instant startedAt);

  boolean existsByStatus(CodeSystemJobExecution.Status status);

  Optional<CodeSystemJobExecution> findFirstByStatusOrderByCompletedAtDesc(
      CodeSystemJobExecution.Status status);

  Optional<CodeSystemJobExecution> findFirstByStatusInOrderByCompletedAtDesc(
      Collection<CodeSystemJobExecution.Status> statuses);
}
