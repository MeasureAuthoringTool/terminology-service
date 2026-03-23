package gov.cms.madie.terminology.clients;

import gov.cms.madie.models.dto.UserRolesDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

  @Mock private RestTemplate userServiceRestTemplate;
  @InjectMocks private UserServiceClient userServiceClient;

  @Captor private ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor;

  private static final String HARP_ID = "testUser";
  private static final String TOKEN = "token";
  private static final String BASE_URL = "http://localhost:8088/api";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userServiceClient, "userServiceBaseUrl", BASE_URL);
  }

  @Test
  void testGetUserRoles() {
    String url = BASE_URL + "/users/" + HARP_ID + "/roles";
    UserRolesDto expected =
        UserRolesDto.builder().harpId(HARP_ID).roles(List.of("MADiE-User")).build();

    when(userServiceRestTemplate.exchange(
            eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class)))
        .thenReturn(ResponseEntity.ok(expected));

    UserRolesDto result = userServiceClient.getUserRoles(HARP_ID, TOKEN);

    assertThat(result, is(notNullValue()));
    assertThat(result.getHarpId(), is(equalTo(HARP_ID)));
    verify(userServiceRestTemplate, times(1))
        .exchange(eq(url), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserRolesDto.class));
    HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION), is(equalTo("Bearer " + TOKEN)));
  }

  @Test
  void testGetUserRolesWithException() {
    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class)))
        .thenThrow(new RestClientException("Connection error"));

    UserRolesDto result = userServiceClient.getUserRoles(HARP_ID, TOKEN);

    assertThat(result, is(nullValue()));
    verify(userServiceRestTemplate, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  void testGetUserRolesReturnsNullWhenHarpIdIsBlank() {
    UserRolesDto result = userServiceClient.getUserRoles(null, TOKEN);
    assertThat(result, is(nullValue()));

    result = userServiceClient.getUserRoles("", TOKEN);
    assertThat(result, is(nullValue()));

    verifyNoInteractions(userServiceRestTemplate);
  }
}
