package com.tn.service.autoconfig;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.tn.service.logging.EdgeBaggageFilter;
import com.tn.service.logging.RefusalLogger;
import com.tn.service.logging.RequestBaggageFilter;
import com.tn.service.logging.RequestLoggingFilter;

/**
 * Correlated log lines for a servlet service. The request's trace is started by Spring Boot's observation filter
 * ({@code Ordered.HIGHEST_PRECEDENCE + 1}); the request's own baggage is held right after it, and an edge
 * ({@value #EDGE}{@code =true}) discards the {@code user.id} (and legacy {@code userId}) it is sent right before it. Each request is logged inside both, once it
 * has been handled. Why a request was refused is logged while it is handled, unless {@value #REFUSALS}{@code =false}.
 */
@AutoConfiguration(
  afterName = {
    "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
    "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration"
  }
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LoggingAutoConfiguration
{
  public static final String EDGE = "tn.logging.edge";
  public static final String REFUSALS = "tn.logging.refusals";

  static final int ORDER_EDGE_BAGGAGE = Ordered.HIGHEST_PRECEDENCE;
  static final int ORDER_REQUEST_BAGGAGE = Ordered.HIGHEST_PRECEDENCE + 2;
  static final int ORDER_REQUEST_LOGGING = Ordered.HIGHEST_PRECEDENCE + 3;

  @Bean
  public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter()
  {
    FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
    registration.setOrder(ORDER_REQUEST_LOGGING);

    return registration;
  }

  @Bean
  @ConditionalOnBooleanProperty(name = REFUSALS, matchIfMissing = true)
  public RefusalLogger refusalLogger()
  {
    return new RefusalLogger();
  }

  @Bean
  @ConditionalOnBooleanProperty(EDGE)
  public FilterRegistrationBean<EdgeBaggageFilter> edgeBaggageFilter()
  {
    FilterRegistrationBean<EdgeBaggageFilter> registration = new FilterRegistrationBean<>(new EdgeBaggageFilter());
    registration.setOrder(ORDER_EDGE_BAGGAGE);

    return registration;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(Tracer.class)
  static class TracingConfiguration
  {
    @Bean
    @ConditionalOnBean(Tracer.class)
    FilterRegistrationBean<RequestBaggageFilter> requestBaggageFilter(Tracer tracer)
    {
      FilterRegistrationBean<RequestBaggageFilter> registration = new FilterRegistrationBean<>(new RequestBaggageFilter(tracer));
      registration.setOrder(ORDER_REQUEST_BAGGAGE);

      return registration;
    }
  }
}
