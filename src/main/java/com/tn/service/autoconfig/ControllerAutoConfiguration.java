package com.tn.service.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan("com.tn.service.controller")
public class ControllerAutoConfiguration {}
