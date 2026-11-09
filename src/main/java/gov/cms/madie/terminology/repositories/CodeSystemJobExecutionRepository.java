package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.CodeSystemJobExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface CodeSystemJobExecutionRepository
    extends MongoRepository<CodeSystemJobExecution, String> {

  List<CodeSystemJobExecution> findByStatusAndStartedAtBefore(
      CodeSystemJobExecution.Status status, Instant startedAt);
}
