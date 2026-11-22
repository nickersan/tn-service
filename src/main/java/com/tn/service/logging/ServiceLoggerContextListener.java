package com.tn.service.logging;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Supplier;

import jakarta.annotation.Nonnull;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Names the service on every log line: {@code service.name} and {@code service.version} are put on the Logback logger context, whose
 * properties the Logstash encoder writes on every line, so a service's {@code logback-spring.xml} needs nothing.
 * <p>
 * The name is {@code spring.application.name}, else the artifact in the service's build info; the version is the one in its build info.
 * Either is {@code unknown} without them (a test run from an IDE has no build info). Set once logging is initialized, which clears the
 * logger context, so that only the lines written before it lack them.
 */
public class ServiceLoggerContextListener implements ApplicationListener<ApplicationPreparedEvent>, Ordered
{
  public static final String SERVICE_NAME = "service.name";
  public static final String SERVICE_VERSION = "service.version";

  static final String UNKNOWN = "unknown";

  private static final String APPLICATION_NAME = "spring.application.name";
  private static final String BUILD_INFO_LOCATION = "spring.info.build.location";
  private static final String BUILD_INFO_DEFAULT_LOCATION = "classpath:META-INF/build-info.properties";
  private static final String BUILD_PREFIX = "build.";

  private final Supplier<ILoggerFactory> loggerFactory;

  public ServiceLoggerContextListener()
  {
    this(LoggerFactory::getILoggerFactory);
  }

  ServiceLoggerContextListener(Supplier<ILoggerFactory> loggerFactory)
  {
    this.loggerFactory = loggerFactory;
  }

  @Override
  public void onApplicationEvent(@Nonnull ApplicationPreparedEvent event)
  {
    if (!(loggerFactory.get() instanceof LoggerContext loggerContext)) return;

    ConfigurableApplicationContext applicationContext = event.getApplicationContext();
    BuildProperties buildProperties = buildProperties(applicationContext);

    loggerContext.putProperty(SERVICE_NAME, serviceName(applicationContext.getEnvironment(), buildProperties));
    loggerContext.putProperty(SERVICE_VERSION, orUnknown(buildProperties.getVersion()));
  }

  // After Spring Boot's own listeners, so after logging is initialized.
  @Override
  public int getOrder()
  {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private String serviceName(Environment environment, BuildProperties buildProperties)
  {
    String applicationName = environment.getProperty(APPLICATION_NAME);

    return applicationName != null && !applicationName.isBlank() ? applicationName : orUnknown(buildProperties.getArtifact());
  }

  private String orUnknown(String value)
  {
    return value != null ? value : UNKNOWN;
  }

  // As Spring Boot reads them for its BuildProperties bean, which is not made until the context is refreshed; none when unreadable.
  private BuildProperties buildProperties(ConfigurableApplicationContext applicationContext)
  {
    Resource resource = applicationContext.getResource(applicationContext.getEnvironment().getProperty(BUILD_INFO_LOCATION, BUILD_INFO_DEFAULT_LOCATION));

    try
    {
      return new BuildProperties(resource.exists() ? withoutPrefix(PropertiesLoaderUtils.loadProperties(resource)) : new Properties());
    }
    catch (IOException e)
    {
      return new BuildProperties(new Properties());
    }
  }

  private Properties withoutPrefix(Properties source)
  {
    Properties target = new Properties();
    for (String key : source.stringPropertyNames())
    {
      if (key.startsWith(BUILD_PREFIX)) target.put(key.substring(BUILD_PREFIX.length()), source.getProperty(key));
    }

    return target;
  }
}
