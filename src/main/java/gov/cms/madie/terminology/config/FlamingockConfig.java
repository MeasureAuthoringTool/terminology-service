package gov.cms.madie.terminology.config;

import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.targetsystem.mongodb.springdata.MongoDBSpringDataTargetSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class FlamingockConfig {

  @Bean
  public MongoDBSpringDataTargetSystem mongoTargetSystem(MongoTemplate mongoTemplate) {
    return new MongoDBSpringDataTargetSystem("terminology", mongoTemplate);
  }

  @Bean
  public CommunityAuditStore auditStore(MongoDBSpringDataTargetSystem mongoTargetSystem) {
    return MongoDBSyncAuditStore.from(mongoTargetSystem);
  }
}
