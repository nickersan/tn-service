package com.tn.service.autoconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class LoggingAutoConfigurationTest
{
  private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

  @Test
  void shouldLogRequestsAndHoldTheirBaggageInsideTheTrace()
  {
    contextRunner.withBean(Tracer.class, () -> mock(Tracer.class)).run(
      context ->
      {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, context.getBean("requestBaggageFilter", FilterRegistrationBean.class).getOrder());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 3, context.getBean("requestLoggingFilter", FilterRegistrationBean.class).getOrder());
        assertFalse(context.containsBean("edgeBaggageFilter"));
      }
    );
  }

  @Test
  void shouldDiscardTheCallersUserBeforeTheTraceIsReadOnlyAtAnEdge()
  {
    contextRunner.withPropertyValues("tn.logging.edge=true").run(
      context -> assertEquals(Ordered.HIGHEST_PRECEDENCE, context.getBean("edgeBaggageFilter", FilterRegistrationBean.class).getOrder())
    );
    contextRunner.withPropertyValues("tn.logging.edge=false").run(context -> assertFalse(context.containsBean("edgeBaggageFilter")));
  }

  @Test
  void shouldHoldNoBaggageWithoutATracer()
  {
    contextRunner.run(
      context ->
      {
        assertFalse(context.containsBean("requestBaggageFilter"));
        assertTrue(context.containsBean("requestLoggingFilter"));
      }
    );
    contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class)).run(
      context ->
      {
        assertFalse(context.containsBean("requestBaggageFilter"));
        assertTrue(context.containsBean("requestLoggingFilter"));
      }
    );
  }

  @Test
  void shouldDoNothingOutsideAServletService()
  {
    new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class))
      .withPropertyValues("tn.logging.edge=true")
      .run(context -> assertFalse(context.containsBean("requestLoggingFilter")));
  }
}
