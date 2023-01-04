package com.tn.service.api.config;

import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;

import static com.tn.lang.Strings.EMPTY;
import static com.tn.lang.Strings.isNullOrWhitespace;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class PropertyLogger
{
  private final Logger logger;
  private final Map<String, Function<String, String>> valueFormatters;

  public PropertyLogger(Logger logger)
  {
    this(logger, emptyMap());
  }

  public PropertyLogger(Logger logger, Map<String, Function<String, String>> valueFormatters)
  {
    this.logger = logger;
    this.valueFormatters = valueFormatters;
  }

  @EventListener
  public void handleContextRefreshed(ContextRefreshedEvent event)
  {
    ConfigurableEnvironment environment = (ConfigurableEnvironment)event.getApplicationContext().getEnvironment();

    environment.getPropertySources()
      .stream()
      .filter(propertySource -> propertySource instanceof MapPropertySource)
      .map(propertySource -> ((MapPropertySource)propertySource).getSource().keySet())
      .flatMap(Collection::stream)
      .distinct()
      .sorted()
      .forEach(name -> logger.info("{}={}", name, value(environment, name)));
  }

  private String value(ConfigurableEnvironment environment, String name)
  {
    String value = environment.getProperty(name);

    if (isNullOrWhitespace(value)) return EMPTY;

    return valueFormatters.getOrDefault(name, identity()).apply(value);
  }
}
