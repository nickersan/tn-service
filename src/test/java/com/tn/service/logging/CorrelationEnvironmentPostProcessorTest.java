package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

class CorrelationEnvironmentPostProcessorTest
{
  private static final String REMOTE_FIELDS = "management.tracing.baggage.remote-fields";
  private static final String CORRELATION_FIELDS = "management.tracing.baggage.correlation.fields";
  private static final String PROBABILITY = "management.tracing.sampling.probability";
  private static final String PROPAGATION = "management.tracing.propagation.type";

  private final CorrelationEnvironmentPostProcessor postProcessor = new CorrelationEnvironmentPostProcessor();

  @Test
  void shouldSampleEveryRequestAndPropagateW3cByDefault()
  {
    MockEnvironment environment = process(new MockEnvironment());

    assertEquals("1.0", environment.getProperty(PROBABILITY));
    assertEquals("w3c", environment.getProperty(PROPAGATION));
  }

  @Test
  void shouldCarryTheUserSessionAndClientAndPutThemOnEveryLine()
  {
    MockEnvironment environment = process(new MockEnvironment());

    assertEquals("userId,sessionId,client", environment.getProperty(REMOTE_FIELDS));
    assertEquals("userId,sessionId,client", environment.getProperty(CORRELATION_FIELDS));
  }

  @Test
  void shouldMergeTheFieldsTheServiceSets()
  {
    MockEnvironment environment = process(
      new MockEnvironment()
        .withProperty(REMOTE_FIELDS, "tenant, client")
        .withProperty(CORRELATION_FIELDS + "[0]", "tenant")
    );

    assertEquals("tenant,client,userId,sessionId", environment.getProperty(REMOTE_FIELDS));
    assertEquals("tenant,userId,sessionId,client", environment.getProperty(CORRELATION_FIELDS));
  }

  @Test
  void shouldLetTheServicesOwnSamplingAndPropagationWin()
  {
    MockEnvironment environment = process(new MockEnvironment().withProperty(PROBABILITY, "0.5").withProperty(PROPAGATION, "b3"));

    assertEquals("0.5", environment.getProperty(PROBABILITY));
    assertEquals("b3", environment.getProperty(PROPAGATION));
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
