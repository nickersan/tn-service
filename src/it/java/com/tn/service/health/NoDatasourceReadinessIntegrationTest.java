package com.tn.service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import com.tn.service.autoconfig.ControllerAutoConfiguration;

/** The classpath has a datasource driver, so the datasource is excluded explicitly: this is a service that has none and so no {@code db} contributor. */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  classes = ControllerAutoConfiguration.class,
  properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
)
@AutoConfigureTestRestTemplate
@EnableAutoConfiguration
class NoDatasourceReadinessIntegrationTest
{
  @Autowired
  TestRestTemplate testRestTemplate;

  @Test
  void shouldStartAndBeReady_whenTheServiceHasNoDatasource()
  {
    assertEquals("{\"status\":\"UP\"}", testRestTemplate.getForEntity("/actuator/health/readiness", String.class).getBody());
    assertEquals("{\"status\":\"UP\"}", testRestTemplate.getForEntity("/actuator/health/liveness", String.class).getBody());
  }
}
