package gov.cms.madie.terminology.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ManifestCacheEvictJobTest {

  @Autowired private CacheManager cacheManager;

  @Autowired private ManifestCacheEvictJob manifestCacheEvictJob;

  @Test
  void clearManifestCache() {
    cacheManager.getCache("manifest-list").put("testKey", "testValue");
    assertThat(cacheManager.getCache("manifest-list").get("testKey")).isNotNull();

    manifestCacheEvictJob.clearManifestCache();

    assertThat(cacheManager.getCache("manifest-list").get("testKey")).isNull();
  }
}
