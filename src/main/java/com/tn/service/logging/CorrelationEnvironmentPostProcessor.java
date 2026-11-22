package com.tn.service.logging;

import static com.tn.service.logging.RequestBaggage.CLIENT;
import static com.tn.service.logging.RequestBaggage.SESSION_ID;
import static com.tn.service.logging.RequestBaggage.USER_ID;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Gives every service that depends on tn-service correlated log lines: a trace continued from the W3C {@code traceparent} it is sent, or
 * started, on every request, and the {@code userId}, {@code sessionId} and {@code client} baggage carried between services and put on
 * every line.
 * <p>
 * Sampling and propagation are defaults of the lowest priority, so a service's own setting wins. The baggage fields are merged with what
 * the service sets, not replaced by it, so a service can carry fields of its own as well. No span is exported: tn-service brings no span
 * exporter, so tracing is for log correlation only.
 */
public class CorrelationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{
  static final String DEFAULTS = "tnServiceCorrelationDefaults";
  static final String BAGGAGE = "tnServiceCorrelationBaggage";

  static final String REMOTE_FIELDS = "management.tracing.baggage.remote-fields";
  static final String CORRELATION_FIELDS = "management.tracing.baggage.correlation.fields";
  static final List<String> FIELDS = List.of(USER_ID, SESSION_ID, CLIENT);

  private static final Map<String, Object> DEFAULT_PROPERTIES = Map.of(
    "management.tracing.sampling.probability", "1.0",
    "management.tracing.propagation.type", "w3c"
  );

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application)
  {
    environment.getPropertySources().addLast(new MapPropertySource(DEFAULTS, DEFAULT_PROPERTIES));

    Map<String, Object> baggage = new LinkedHashMap<>();
    baggage.put(REMOTE_FIELDS, withFields(environment, REMOTE_FIELDS));
    baggage.put(CORRELATION_FIELDS, withFields(environment, CORRELATION_FIELDS));

    environment.getPropertySources().addFirst(new MapPropertySource(BAGGAGE, baggage));
  }

  // After the application's own configuration files have been loaded, so that the fields they set can be merged.
  @Override
  public int getOrder()
  {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private String withFields(ConfigurableEnvironment environment, String name)
  {
    Set<String> fields = new LinkedHashSet<>(Binder.get(environment).bind(name, Bindable.listOf(String.class)).orElse(List.of()));
    fields.addAll(FIELDS);

    return String.join(",", fields);
  }
}
