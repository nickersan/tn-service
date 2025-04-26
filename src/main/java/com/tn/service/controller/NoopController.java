package com.tn.service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.tn.service.api.NoopApi;

@Slf4j
@RestController
@ConditionalOnProperty(name = "tn.service.noop.enabled", havingValue = "true", matchIfMissing = true)
public class NoopController implements NoopApi
{
  public ResponseEntity<Void> noop()
  {
    log.debug("Received GET /noop");
    return ResponseEntity.ok().build();
  }
}
