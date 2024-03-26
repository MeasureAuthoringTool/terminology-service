package gov.cms.madie.terminology.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Document

public class CodeSystem {
    @Id private String id;
    private String name;
    private String version;
    private Identifier identifier; // identifier[0] of identifier List
    private Meta meta;
    private Instant lastUpdated;
}
