package com.tn.service.api.config;

import static java.util.function.Function.identity;

import static com.tn.lang.Strings.EMPTY;
import static com.tn.lang.Strings.isNullOrWhitespace;

import java.nio.CharBuffer;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class PropertyLogger
{
  private final Logger logger;
  private final Collection<ValueFormatter> valueFormatters;

  public PropertyLogger(Logger logger, ValueFormatter... valueFormatters)
  {
    this.logger = logger;
    this.valueFormatters = List.of(valueFormatters);
  }

  public static ValueFormatter sensitive(String nameRegex)
  {
    return new ValueFormatter()
    {
      @Override
      public boolean matches(String name)
      {
        return name.matches(nameRegex);
      }

      @Override
      public String format(String value)
      {
        return CharBuffer.allocate(value.length()).toString().replace('\0', 'X');
      }
    };
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

    return valueFormatter(name).apply(value);
  }

  private Function<String, String> valueFormatter(String name)
  {
    return this.valueFormatters.stream()
      .filter(valueFormatter -> valueFormatter.matches(name))
      .findFirst()
      .map(this::formatWith)
      .orElse(identity());
  }

  private Function<String, String> formatWith(ValueFormatter valueFormatter)
  {
    return valueFormatter::format;
  }

  public interface ValueFormatter
  {
    boolean matches(String name);

    String format(String value);
  }
}
