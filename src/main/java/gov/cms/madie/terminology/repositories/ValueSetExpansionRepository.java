package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.MadieValueSet;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ValueSetExpansionRepository extends MongoRepository<MadieValueSet, String> {
  MadieValueSet findByUrlAndVersionIsNull(String url);

  Optional<MadieValueSet> findByUrlAndVersion(String url, String version);

  default Optional<MadieValueSet> findByUrlAndVersionOrNull(String url, String version) {
    if (version == null) {
      return findByUrl(url);
    }
    return findByUrlAndVersion(url, version);
  }

  Optional<MadieValueSet> findByUrl(String url);
}
