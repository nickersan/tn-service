package com.tn.service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import com.tn.service.autoconfig.ControllerAutoConfiguration;

/** A service that exposes an endpoint of its own, as okayat-bff exposes env, keeps it. */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  classes = ControllerAutoConfiguration.class,
  properties = "management.endpoints.web.exposure.include=env"
)
@AutoConfigureTestRestTemplate
@EnableAutoConfiguration
class HealthExposureIntegrationTest
{
  @Autowired
  TestRestTemplate testRestTemplate;

  @Test
  void shouldKeepTheServicesOwnEndpointAndAddHealthAndResilience()
  {
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/env", String.class).getStatusCode());
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/health/readiness", String.class).getStatusCode());
    assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/actuator/resilience", String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, testRestTemplate.getForEntity("/actuator/info", String.class).getStatusCode());
  }
}
