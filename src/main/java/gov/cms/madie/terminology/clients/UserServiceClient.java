package gov.cms.madie.terminology.clients;

import gov.cms.madie.models.dto.UserRolesDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceClient {

  private final RestTemplate userServiceRestTemplate;

  @Value("${madie.user-service.base-url}")
  private String userServiceBaseUrl;

  /**
   * Fetches UserRolesDto from the user service.
   *
   * @param harpId: HARP ID to fetch UserRolesDto for
   * @param accessToken: Bearer token for authorization
   * @return UserRolesDto which contains the HARP ID and associated roles, or null if service call
   *     fails
   */
  public UserRolesDto getUserRoles(String harpId, String accessToken) {
    log.debug("Requesting user roles for HARP ID: [{}]", harpId);
    if (!StringUtils.hasText(harpId)) {
      log.debug("Skipping user roles fetch — harpId is blank");
      return null;
    }

    String url = userServiceBaseUrl + "/users/" + harpId + "/roles";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    try {
      log.debug("Calling user-service to request user roles for HARP ID: [{}]", harpId);
      ResponseEntity<UserRolesDto> responseEntity =
          userServiceRestTemplate.exchange(url, HttpMethod.GET, request, UserRolesDto.class);
      UserRolesDto response = responseEntity.getBody();
      log.debug("Successfully retrieved user roles for HARP ID: [{}]", harpId);
      return response;
    } catch (Exception e) {
      log.error(
          "Failed to fetch user roles from user service for HARP ID: [{}]: {}",
          harpId,
          e.getMessage(),
          e);
      return null;
    }
  }
}
