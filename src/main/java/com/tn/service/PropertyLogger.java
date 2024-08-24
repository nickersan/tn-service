package com.tn.service;

import java.nio.CharBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import com.tn.lang.Strings;

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

  public static PropertyLogger.ValueFormatter sensitive(String nameRegex)
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
        return CharBuffer.allocate(value.length()).toString().replace('\u0000', '*');
      }
    };
  }

  public void onApplicationEvent(@Nonnull ApplicationPreparedEvent event)
  {
    if (this.firstRun)
    {
      this.firstRun = false;
      this.printProperties(event.getApplicationContext().getEnvironment());
    }
  }

  private void printProperties(ConfigurableEnvironment environment)
  {
    environment.getPropertySources().stream()
      .filter(propertySource -> propertySource instanceof MapPropertySource)
      .map(propertySource -> ((MapPropertySource)propertySource).getSource().keySet())
      .flatMap(Collection::stream)
      .distinct()
      .sorted()
      .forEach(name -> this.logger.info("{}={}", name, value(environment, name)));
  }

  private String value(ConfigurableEnvironment environment, String name)
  {
    String value = environment.getProperty(name);
    return Strings.isNullOrWhitespace(value) ? Strings.EMPTY : this.valueFormatter(name).apply(value);
  }

  private Function<String, String> valueFormatter(String name)
  {
    return this.valueFormatters.stream()
      .filter((valueFormatter) -> valueFormatter.matches(name))
      .findFirst()
      .map(this::formatWith)
      .orElse(Function.identity());
  }

  private Function<String, String> formatWith(ValueFormatter valueFormatter)
  {
    Objects.requireNonNull(valueFormatter);
    return valueFormatter::format;
  }

  public interface ValueFormatter
  {
    boolean matches(String name);

    String format(String value);
  }
}