package com.tn.service.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@AutoConfiguration
@ComponentScan("com.tn.service.controller")
public class ControllerAutoConfiguration
{
  @Bean
  public CommonsRequestLoggingFilter requestLoggingFilter()
  {
    CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
    loggingFilter.setIncludeClientInfo(true);
    loggingFilter.setIncludeQueryString(true);
    loggingFilter.setIncludePayload(true);
    loggingFilter.setMaxPayloadLength(64000);

    return loggingFilter;
  }
}
