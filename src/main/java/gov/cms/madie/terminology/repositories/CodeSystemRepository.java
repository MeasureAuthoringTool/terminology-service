package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.CodeSystem;
import lombok.NonNull;
import org.springframework.data.mongodb.repository.MongoRepository;

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
}
