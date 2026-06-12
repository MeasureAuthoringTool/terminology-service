package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.MadieValueSet;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ValueSetExpansionRepository extends MongoRepository<MadieValueSet, String> {
  MadieValueSet findByUrlAndVersionIsNull(String url);

  Optional<MadieValueSet> findByUrlAndVersion(String url, String version);

  default Optional<MadieValueSet> findByUrlAndVersionOrNull(String url, String version) {
    if (version == null) {
      List<MadieValueSet> valueSets = findByUrlOrderByVersionAsc(url);
      if (valueSets.isEmpty()) {
        return Optional.empty();
      }
      if (valueSets.get(0).getVersion() == null) {
        return Optional.of(valueSets.get(0));
      }
      return Optional.of(valueSets.get(valueSets.size() - 1));
    }
    return findByUrlAndVersion(url, version);
  }

  List<MadieValueSet> findByUrlOrderByVersionAsc(String url);
}
