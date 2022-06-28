package gov.cms.madie.terminology.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import gov.cms.madie.terminology.models.UmlsUser;

import java.util.Optional;

public interface UmlsUserRepository extends MongoRepository<UmlsUser, String> {
  Optional<UmlsUser> findByHarpId(String harpId);

  Optional<UmlsUser> findByHarpIdAndApiKey(String harpId, String apiKey);
}
