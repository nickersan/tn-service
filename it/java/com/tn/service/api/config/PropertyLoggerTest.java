package com.tn.service.api.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@SuppressWarnings("SpringBootApplicationProperties")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "x=y")
@ContextConfiguration(classes = PropertyLoggerTest.TestConfiguration.class)
public class PropertyLoggerTest
{
  @Autowired
  Logger logger;

  @Test
  void shouldLogPropertiesWhenApplicationContextRefreshed()
  {
    verify(logger).info("{}={}", "x", "y");
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
      return new PropertyLogger(logger);
    }
  }
}
