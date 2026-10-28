package com.tn.service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tn.service.autoconfig.ControllerAutoConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ControllerAutoConfiguration.class)
@AutoConfigureTestRestTemplate
@EnableAutoConfiguration
class HealthApiIntegrationTest
{
  @Autowired
  TestRestTemplate testRestTemplate;

  @Test
  void shouldBeReadyAndLive_whenStartedWithNoDatasource()
  {
    assertEquals("{\"status\":\"UP\"}", get("/actuator/health/readiness").getBody());
    assertEquals("{\"status\":\"UP\"}", get("/actuator/health/liveness").getBody());
  }

  @Test
  void shouldShowStatusNotDetail()
  {
    String health = get("/actuator/health").getBody();

    assertTrue(health.contains("\"status\":\"UP\""));
    assertFalse(health.contains("components") || health.contains("details"), health);
  }

  @Test
  void shouldAnswerNoopForAPersonChecking()
  {
    ResponseEntity<String> response = get("/noop");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody() == null || response.getBody().isEmpty());
  }

  @Test
  void shouldHaveNoGroupsWhenTheServiceCallsNoOther()
  {
    assertEquals("{\"groups\":{}}", get("/actuator/resilience").getBody());
  }

  @Test
  void shouldExposeNothingElse()
  {
    for (String endpoint : new String[] {"env", "info", "metrics", "beans", "configprops"})
    {
      assertEquals(HttpStatus.NOT_FOUND, get("/actuator/" + endpoint).getStatusCode(), endpoint);
    }
  }

  private ResponseEntity<String> get(String path)
  {
    return testRestTemplate.getForEntity(path, String.class);
  }
}
