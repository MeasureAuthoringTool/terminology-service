package gov.cms.madie.terminology.models;
import java.time.Instant;
import java.util.List;
public class Meta {
    private String versionId;
    // This is instant at time of query, not to be confused with the actual resource.meta.lastUpdated, which is a timestamp of when vsac posted the version
    private Instant lastUpdated;
    private List<String> profile;
}
