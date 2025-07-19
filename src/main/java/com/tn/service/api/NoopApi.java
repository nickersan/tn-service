package com.tn.service.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

public interface NoopApi
{
  @GetMapping("/noop")
  ResponseEntity<Void> noop();
}
