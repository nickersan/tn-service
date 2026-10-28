package com.tn.service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

class HealthEnvironmentPostProcessorTest
{
  private static final String INCLUDE = "management.endpoints.web.exposure.include";

  private final HealthEnvironmentPostProcessor postProcessor = new HealthEnvironmentPostProcessor();

  @Test
  void shouldExposeOnlyHealthAndResilienceByDefault()
  {
    assertEquals("health,resilience", process(new MockEnvironment()).getProperty(INCLUDE));
  }

  @Test
  void shouldMergeWhatTheServiceExposes()
  {
    assertEquals("env,health,resilience", process(new MockEnvironment().withProperty(INCLUDE, "env")).getProperty(INCLUDE));
  }

  @Test
  void shouldNotRepeatWhatTheServiceAlreadyExposes()
  {
    assertEquals("health,env,resilience", process(new MockEnvironment().withProperty(INCLUDE, " health , env ,,")).getProperty(INCLUDE));
  }

  @Test
  void shouldLeaveExposureAlone_whenTheServiceExposesEverything()
  {
    assertEquals("*", process(new MockEnvironment().withProperty(INCLUDE, "*")).getProperty(INCLUDE));
  }

  @Test
  void shouldEnableTheProbesWithoutShowingDetail()
  {
    MockEnvironment environment = process(new MockEnvironment());

    assertEquals("true", environment.getProperty("management.endpoint.health.probes.enabled"));
    assertEquals("never", environment.getProperty("management.endpoint.health.show-details"));
  }

  @Test
  void shouldMakeReadinessTheReadinessStateAndTheDatasourceOnly()
  {
    assertEquals("readinessState,db", process(new MockEnvironment()).getProperty("management.endpoint.health.group.readiness.include"));
  }

  @Test
  void shouldNotChangeLiveness()
  {
    assertNull(process(new MockEnvironment()).getProperty("management.endpoint.health.group.liveness.include"));
  }

  @Test
  void shouldLetTheServicesOwnSettingsWin()
  {
    MockEnvironment environment = process(
      new MockEnvironment()
        .withProperty("management.endpoint.health.show-details", "always")
        .withProperty("management.endpoint.health.probes.enabled", "false")
        .withProperty("management.endpoint.health.group.readiness.include", "readinessState")
    );

    assertEquals("always", environment.getProperty("management.endpoint.health.show-details"));
    assertEquals("false", environment.getProperty("management.endpoint.health.probes.enabled"));
    assertEquals("readinessState", environment.getProperty("management.endpoint.health.group.readiness.include"));
  }

  @Test
  void shouldRunAfterTheServicesConfigurationIsLoaded()
  {
    assertEquals(Ordered.LOWEST_PRECEDENCE, postProcessor.getOrder());
  }

  private MockEnvironment process(MockEnvironment environment)
  {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }
}
