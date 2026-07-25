package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.MadieValueSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ValueSetExpansionRepository extends MongoRepository<MadieValueSet, String> {
  MadieValueSet findByUrlAndVersionIsNull(String url);

  Page<MadieValueSet> findByUrlContainingIgnoreCase(String searchTerm, Pageable pageable);

  Optional<MadieValueSet> findByUrlAndVersion(String url, String version);

  default Optional<MadieValueSet> findByUrlAndVersionOrNull(String url, String version) {
    if (version == null) {
      return findFirstByUrl(
          url, Sort.by(Sort.Order.by("version").with(Sort.Direction.DESC).nullsFirst()));
    }
    return findByUrlAndVersion(url, version);
  }

  Optional<MadieValueSet> findFirstByUrl(String url, Sort sort);
}
