package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.CodeSystem;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CodeSystemRepository extends MongoRepository<CodeSystem, String> {
  Optional<CodeSystem> findById(String id);

  Optional<CodeSystem> findByNameAndVersion(String name, String version);
}
