package com.tn.service.health;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Gives every service that depends on tn-service the liveness and readiness health checks and the {@code resilience} endpoint.
 * <p>
 * The probe settings are defaults of the lowest priority, so a service's own setting wins. The endpoints exposed are merged with what the
 * service exposes, not replaced by it, so a service that exposes {@code env} keeps it. Only {@code health} and {@code resilience} are added.
 * <p>
 * Readiness is the readiness state and, where the service has one, its datasource ({@code db}); a name that matches nothing is ignored.
 * It never includes another service.
 */
public class HealthEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{
  static final String DEFAULTS = "tnServiceHealthDefaults";
  static final String EXPOSURE = "tnServiceHealthExposure";

  static final String INCLUDE = "management.endpoints.web.exposure.include";
  static final List<String> EXPOSED = List.of("health", "resilience");

  private static final Map<String, Object> DEFAULT_PROPERTIES = Map.of(
    "management.endpoint.health.probes.enabled", "true",
    "management.endpoint.health.show-details", "never",
    "management.endpoint.health.group.readiness.include", "readinessState,db"
  );

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application)
  {
    environment.getPropertySources().addLast(new MapPropertySource(DEFAULTS, DEFAULT_PROPERTIES));

    String own = environment.getProperty(INCLUDE);
    Set<String> include = new LinkedHashSet<>();
    for (String endpoint : StringUtils.commaDelimitedListToStringArray(own))
    {
      if (StringUtils.hasText(endpoint))
      {
        include.add(endpoint.trim());
      }
    }

    if (!include.contains("*"))
    {
      include.addAll(EXPOSED);
      environment.getPropertySources().addFirst(new MapPropertySource(EXPOSURE, Map.of(INCLUDE, String.join(",", include))));
    }
  }

  // After the application's own configuration files have been loaded, so that what they expose can be merged.
  @Override
  public int getOrder()
  {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
