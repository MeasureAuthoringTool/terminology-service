package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.CodeSystem;
import lombok.NonNull;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CodeSystemRepository extends MongoRepository<CodeSystem, String> {
  @NonNull
  Optional<CodeSystem> findById(@NonNull String id);

  Optional<CodeSystem> findByNameAndVersionFhirVersion(String name, String version);

  Optional<CodeSystem> findByNameAndVersionVsacVersion(String name, String version);

  Optional<CodeSystem> findByOidAndVersionFhirVersion(String oid, String version);

  Optional<CodeSystem> findByFullUrlAndVersionFhirVersion(String fullUrl, String version);

  List<CodeSystem> findAllByOid(String oid);

  List<CodeSystem> findAllByFullUrl(String url);

  List<CodeSystem> findAllByFullUrl(String url, Limit limit);

  Page<CodeSystem> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);

  Page<CodeSystem> findAllByNameContainingIgnoreCase(String name, Pageable pageable);

  Page<CodeSystem> findAllByVersionFhirVersionContainingIgnoreCase(
      String version, Pageable pageable);

  Page<CodeSystem> findAllByFullUrlContainingIgnoreCase(String fullUrl, Pageable pageable);

  @Query(
      "{$or: [ "
          + "{ 'title': { $regex: ?0, $options: 'i' } }, "
          + "{ 'name': { $regex: ?0, $options: 'i' } }, "
          + "{ 'version.fhirVersion': { $regex: ?0, $options: 'i' } }, "
          + "{ 'fullUrl': { $regex: ?0, $options: 'i' } }, "
          + "] }")
  Page<CodeSystem> findAllByAnyFieldContainingIgnoreCase(String searchText, Pageable pageable);
}
