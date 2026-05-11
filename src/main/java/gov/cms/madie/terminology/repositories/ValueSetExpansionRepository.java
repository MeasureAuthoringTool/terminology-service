package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.MadieValueSet;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ValueSetExpansionRepository extends MongoRepository<MadieValueSet, String> {
  MadieValueSet findByUrlAndVersionIsNull(String url);

  Optional<MadieValueSet> findByUrlAndVersion(String url, String version);
}
