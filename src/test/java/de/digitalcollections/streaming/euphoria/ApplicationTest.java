package de.digitalcollections.streaming.euphoria;

import de.digitalcollections.streaming.euphoria.config.SpringConfigSecurity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic integration tests for webapp endpoints.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
  Application.class, SpringConfigSecurity.class
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
) // set random webapp/server port
@TestPropertySource(properties = {"management.server.port=0"}) // set random management port
public class ApplicationTest {

  @LocalServerPort
  private int port;

  @LocalManagementPort
  private int monitoringPort;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Value("${spring.security.user.name}")
  private String monitoringUsername;

  @Value("${spring.security.user.password}")
  private String monitoringPassword;

  @Test
  public void shouldReturn200WhenSendingRequestToRoot() throws Exception {
    @SuppressWarnings("rawtypes")
    ResponseEntity<String> entity = this.testRestTemplate.getForEntity(
      "http://localhost:" + this.port + "/", String.class
    );

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void shouldReturn200WhenSendingRequestToManagementEndpoint() throws Exception {
    ResponseEntity<Map> entity = this.testRestTemplate.getForEntity(
      "http://localhost:" + this.monitoringPort + "/monitoring/health", Map.class
    );

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void shouldReturn200WhenSendingAuthorizedRequestToSensitiveManagementEndpoint() throws Exception {
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> entity = this.testRestTemplate.withBasicAuth(monitoringUsername, monitoringPassword).getForEntity(
      "http://localhost:" + this.monitoringPort + "/monitoring/env", Map.class
    );

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void shouldReturn401WhenSendingUnauthorizedRequestToSensitiveManagementEndpoint() throws Exception {
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> entity = this.testRestTemplate.getForEntity(
      "http://localhost:" + this.monitoringPort + "/monitoring/env", Map.class
    );

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

}
