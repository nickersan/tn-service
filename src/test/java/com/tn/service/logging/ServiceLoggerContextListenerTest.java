package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.Nonnull;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.ILoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

class ServiceLoggerContextListenerTest
{
  private static final String SERVICE_NAME = "service.name";
  private static final String SERVICE_VERSION = "service.version";
  private static final String APPLICATION_NAME = "spring.application.name";
  private static final String BUILD_INFO_LOCATION = "spring.info.build.location";
  private static final String BUILD_INFO = "build.artifact=okayat-spot-service\nbuild.name=OK AT spot service\nbuild.version=1.12.0\n";

  private final LoggerContext loggerContext = new LoggerContext();
  private final ServiceLoggerContextListener listener = new ServiceLoggerContextListener(() -> loggerContext);

  @TempDir
  Path directory;

  @Test
  void shouldNameTheServiceByItsApplicationNameAndItsBuildVersion() throws IOException
  {
    prepare(new MockEnvironment().withProperty(APPLICATION_NAME, "spot-service").withProperty(BUILD_INFO_LOCATION, buildInfo(BUILD_INFO)));

    assertEquals("spot-service", loggerContext.getProperty(SERVICE_NAME));
    assertEquals("1.12.0", loggerContext.getProperty(SERVICE_VERSION));
  }

  @Test
  void shouldNameTheServiceByItsBuildArtifactWithoutAnApplicationName() throws IOException
  {
    prepare(new MockEnvironment().withProperty(APPLICATION_NAME, " ").withProperty(BUILD_INFO_LOCATION, buildInfo(BUILD_INFO)));

    assertEquals("okayat-spot-service", loggerContext.getProperty(SERVICE_NAME));
    assertEquals("1.12.0", loggerContext.getProperty(SERVICE_VERSION));
  }

  @Test
  void shouldNotKnowTheNameOrVersionWithoutBuildInfo()
  {
    prepare(new MockEnvironment().withProperty(BUILD_INFO_LOCATION, directory.resolve("missing.properties").toUri().toString()));

    assertEquals("unknown", loggerContext.getProperty(SERVICE_NAME));
    assertEquals("unknown", loggerContext.getProperty(SERVICE_VERSION));
  }

  @Test
  void shouldNotKnowTheVersionWhenTheBuildInfoHasNone() throws IOException
  {
    prepare(new MockEnvironment().withProperty(APPLICATION_NAME, "spot-service").withProperty(BUILD_INFO_LOCATION, buildInfo("other.version=1\n")));

    assertEquals("spot-service", loggerContext.getProperty(SERVICE_NAME));
    assertEquals("unknown", loggerContext.getProperty(SERVICE_VERSION));
  }

  @Test
  void shouldNotKnowTheNameOrVersionWhenTheBuildInfoCannotBeRead()
  {
    ApplicationPreparedEvent event = event(new MockEnvironment());
    ((GenericApplicationContext)event.getApplicationContext()).setResourceLoader(new UnreadableResourceLoader());

    listener.onApplicationEvent(event);

    assertEquals("unknown", loggerContext.getProperty(SERVICE_NAME));
    assertEquals("unknown", loggerContext.getProperty(SERVICE_VERSION));
  }

  @Test
  void shouldReadTheServicesOwnBuildInfoByDefault()
  {
    prepare(new MockEnvironment());

    assertEquals("unknown", loggerContext.getProperty(SERVICE_VERSION));
  }

  @Test
  void shouldLeaveALoggerOtherThanLogbackAlone()
  {
    new ServiceLoggerContextListener(() -> mock(ILoggerFactory.class)).onApplicationEvent(event(new MockEnvironment()));

    assertNull(loggerContext.getProperty(SERVICE_NAME));
  }

  @Test
  void shouldRunOnceLoggingIsInitialized()
  {
    assertEquals(Ordered.LOWEST_PRECEDENCE, listener.getOrder());
  }

  private void prepare(MockEnvironment environment)
  {
    listener.onApplicationEvent(event(environment));
  }

  private ApplicationPreparedEvent event(MockEnvironment environment)
  {
    GenericApplicationContext applicationContext = new GenericApplicationContext();
    applicationContext.setEnvironment(environment);

    return new ApplicationPreparedEvent(new SpringApplication(), new String[0], applicationContext);
  }

  private static class UnreadableResourceLoader extends DefaultResourceLoader
  {
    @Override
    @Nonnull
    public Resource getResource(@Nonnull String location)
    {
      return new AbstractResource()
      {
        @Override
        public boolean exists()
        {
          return true;
        }

        @Override
        @Nonnull
        public String getDescription()
        {
          return location;
        }

        @Override
        @Nonnull
        public InputStream getInputStream() throws IOException
        {
          throw new IOException("unreadable");
        }
      };
    }
  }

  private String buildInfo(String content) throws IOException
  {
    return Files.writeString(directory.resolve("build-info.properties"), content).toUri().toString();
  }
}
