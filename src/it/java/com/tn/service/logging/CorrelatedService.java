package com.tn.service.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.tn.service.autoconfig.ControllerAutoConfiguration;
import com.tn.service.security.AccessTokenVerifier;
import com.tn.service.security.DelegateUserToken;

/**
 * A service that logs while it handles a request, verifies the token it is sent and calls another service with a client from Spring's
 * {@link RestClient.Builder}, as a tn service does.
 */
@Configuration
@EnableAutoConfiguration
@Import({ControllerAutoConfiguration.class, CorrelatedService.Target.class})
class CorrelatedService
{
  static final String ISSUER = "tn";
  static final KeyPair KEY_PAIR = Jwts.SIG.RS256.keyPair().build();

  @Bean
  AccessTokenVerifier accessTokenVerifier()
  {
    return new AccessTokenVerifier(KEY_PAIR.getPublic(), ISSUER);
  }

  static String token(String subject)
  {
    return Jwts.builder()
      .subject(subject)
      .issuer(ISSUER)
      .claim("token_use", "access")
      .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
      .signWith(KEY_PAIR.getPrivate())
      .compact();
  }

  @RestController
  @Slf4j
  static class Target
  {
    private final RestClient restClient;
    private final AccessTokenVerifier accessTokenVerifier;

    Target(RestClient.Builder restClientBuilder, AccessTokenVerifier accessTokenVerifier)
    {
      this.restClient = restClientBuilder.build();
      this.accessTokenVerifier = accessTokenVerifier;
    }

    @GetMapping("/logged")
    void logged()
    {
      log.info("first");
      log.info("second");
    }

    @GetMapping("/called")
    void called(@RequestParam("to") String to)
    {
      log.info("calling");
      restClient.get().uri(to).retrieve().toBodilessEntity();
      log.info("called");
    }

    @GetMapping("/verified")
    void verified(@RequestHeader(name = DelegateUserToken.HEADER, required = false) String token, @RequestParam("to") String to)
    {
      log.info("verifying");
      if (token != null) accessTokenVerifier.subjectRequired(token);
      log.info("verified");
      restClient.get().uri(to).retrieve().toBodilessEntity();
      log.info("called");
    }

    @GetMapping("/own-fields")
    void ownFields(@RequestHeader(DelegateUserToken.HEADER) String token)
    {
      accessTokenVerifier.subjectRequired(token);
      log.info("own fields", kv("userId", "own-user"), kv("sessionId", "own-session"), kv("client", "own-client"));
    }
  }
}
