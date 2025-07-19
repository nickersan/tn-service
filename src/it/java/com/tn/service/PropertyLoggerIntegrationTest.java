package com.tn.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = PropertyLoggerIntegrationTest.TestConfiguration.class)
@SuppressWarnings("SpringBootApplicationProperties")
@TestPropertySource(properties = {"x=y", "secret=XXXXX"})
public class PropertyLoggerIntegrationTest
{
  @Autowired
  ConfigurableApplicationContext configurableApplicationContext;
  @Autowired
  ApplicationEventPublisher publisher;
  @Autowired
  Logger logger;

  @Test
  void shouldLogPropertiesWhenApplicationContextRefreshed()
  {
    publisher.publishEvent(new ApplicationPreparedEvent(mock(SpringApplication.class), new String[0], configurableApplicationContext));

    verify(logger).info("{}={}", "x", "y");
    verify(logger).info("{}={}", "secret", "XXXXX");
  }

  @Configuration
  static class TestConfiguration
  {
    @Bean
    Logger logger()
    {
      return mock(Logger.class);
    }

    @Bean
    PropertyLogger propertyLogger(Logger logger)
    {
      return new PropertyLogger(logger, PropertyLogger.sensitive(".*sec.*"));
    }
  }
}

