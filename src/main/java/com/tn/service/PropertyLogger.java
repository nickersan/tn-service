package com.tn.service;

import static java.util.function.Function.identity;

import static com.tn.lang.Strings.EMPTY;
import static com.tn.lang.Strings.isNullOrWhitespace;

import java.nio.CharBuffer;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import jakarta.annotation.Nonnull;

import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class PropertyLogger implements ApplicationListener<ApplicationPreparedEvent>
{
  public static final String REGEX_PASSWORD = ".*password.*";
  public static final String REGEX_SECRET = ".*secret.*";

  private final Logger logger;
  private final Collection<ValueFormatter> valueFormatters;

  private boolean firstRun = true;

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

  @Override
  public void onApplicationEvent(@Nonnull ApplicationPreparedEvent event)
  {
    if (firstRun)
    {
      firstRun = false;
      printProperties(event.getApplicationContext().getEnvironment());
    }
  }

  private void printProperties(ConfigurableEnvironment environment)
  {
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
