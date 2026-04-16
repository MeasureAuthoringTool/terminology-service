package gov.cms.madie.terminology.repositories;

import gov.cms.madie.terminology.models.MadieValueSet;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ValueSetExpansionRepository extends MongoRepository<MadieValueSet, String> {}
