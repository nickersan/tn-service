package com.tn.service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tn.resilience.ResilienceConfigurer;
import com.tn.service.autoconfig.ControllerAutoConfiguration;

/** A service whose dependency is down, or whose breaker for it is open, is still ready. */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  classes = ControllerAutoConfiguration.class,
  properties = {"tn.resilience.defaults.sliding-window-size=2", "tn.resilience.defaults.minimum-number-of-calls=2"}
)
@AutoConfigureTestRestTemplate
@EnableAutoConfiguration
class BreakerReadinessIntegrationTest
{
  @Autowired
  TestRestTemplate testRestTemplate;
  @Autowired
  ResilienceConfigurer resilienceConfigurer;

  @Test
  void shouldBeReadyWhileTheBreakerForADownServiceIsOpen()
  {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:1");
    resilienceConfigurer.configure("tn-user-service", builder);
    RestClient client = builder.build();

    for (int i = 0; i < 4; i++)
    {
      assertThrows(RestClientException.class, () -> client.get().uri("/anything").retrieve().toBodilessEntity());
    }

    String resilience = testRestTemplate.getForEntity("/actuator/resilience", String.class).getBody();
    assertTrue(resilience.contains("\"state\":\"OPEN\""), resilience);

    assertEquals(HttpStatus.OK.value(), testRestTemplate.getForEntity("/actuator/health/readiness", String.class).getStatusCode().value());
    assertEquals(HttpStatus.OK.value(), testRestTemplate.getForEntity("/actuator/health/liveness", String.class).getStatusCode().value());
    // the rate limiter refills with time, so only the breaker is compared
    assertEquals(circuitBreaker(resilience), circuitBreaker(testRestTemplate.getForEntity("/actuator/resilience", String.class).getBody()));
  }

  private static String circuitBreaker(String resilience)
  {
    return resilience.substring(resilience.indexOf("\"circuitBreaker\""), resilience.indexOf("\"bulkhead\""));
  }
}
