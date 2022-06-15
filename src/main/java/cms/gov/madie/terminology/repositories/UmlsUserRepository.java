package cms.gov.madie.terminology.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import cms.gov.madie.terminology.models.UmlsUser;

import java.util.Optional;

public interface UmlsUserRepository extends MongoRepository<UmlsUser, String> {
  Optional<UmlsUser> findByHarpId(String harpId);

  Optional<UmlsUser> findByHarpIdAndApiKey(String harpId, String apiKey);
}
