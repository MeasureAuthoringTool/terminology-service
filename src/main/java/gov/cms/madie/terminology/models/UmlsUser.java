package gov.cms.madie.terminology.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Component
public class UmlsUser {
  @Id private String harpId;
  private String apiKey;
  private String tgt;
  private Instant tgtExpiryTime;
  private Instant createdAt;
  private Instant modifiedAt;
}
