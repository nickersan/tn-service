package com.tn.service.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import com.tn.service.autoconfig.ControllerAutoConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ControllerAutoConfiguration.class)
@AutoConfigureTestRestTemplate
@EnableAutoConfiguration
class NoopApiIntegrationTest
{
  @Autowired
  TestRestTemplate testRestTemplate;

  @Test
  void shouldGet()
  {
    ResponseEntity<Void> response = testRestTemplate.getForEntity("/noop", Void.class);
    assertTrue(response.getStatusCode().is2xxSuccessful());
  }
}
