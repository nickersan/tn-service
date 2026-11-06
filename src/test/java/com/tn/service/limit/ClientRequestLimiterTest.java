package com.tn.service.limit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClientRequestLimiterTest
{
  private static final Duration WINDOW = Duration.ofHours(1);

  private final MutableClock clock = new MutableClock();
  private final TooManyRequests refusal = new TooManyRequests();
  private final ClientRequestLimiter limiter = new ClientRequestLimiter(3, WINDOW, clock, () -> refusal);

  @Test
  void shouldAllowUpToTheLimitFromOneClient()
  {
    for (int i = 0; i < 3; i++) assertDoesNotThrow(() -> request("10.0.0.1"));
  }

  @Test
  void shouldLetTheRequestThrough()
  {
    assertTrue(limiter.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
  }

  @Test
  void shouldRefuseTheRequestsBeyondTheLimitWithWhatTheServiceGave()
  {
    for (int i = 0; i < 3; i++) request("10.0.0.1");

    assertSame(refusal, assertThrows(TooManyRequests.class, () -> request("10.0.0.1")));
  }

  @Test
  void shouldCountEachClientSeparately()
  {
    for (int i = 0; i < 3; i++) request("10.0.0.1");

    assertDoesNotThrow(() -> request("10.0.0.2"));
  }

  @Test
  void shouldAllowAgainOnceTheEarlierRequestsFallOutOfTheWindow()
  {
    for (int i = 0; i < 3; i++) request("10.0.0.1");
    clock.advance(WINDOW.plusSeconds(1));

    assertDoesNotThrow(() -> request("10.0.0.1"));
  }

  @Test
  void shouldNotCountARefusedRequestAgainstTheClient()
  {
    for (int i = 0; i < 3; i++) request("10.0.0.1");
    for (int i = 0; i < 10; i++) assertThrows(TooManyRequests.class, () -> request("10.0.0.1"));
    clock.advance(WINDOW.plusSeconds(1));

    for (int i = 0; i < 3; i++) assertDoesNotThrow(() -> request("10.0.0.1"));
  }

  @Test
  void shouldKeepCountingFromTheOldestRequestInTheWindowNotFromTheLast()
  {
    request("10.0.0.1");
    clock.advance(Duration.ofMinutes(30));
    request("10.0.0.1");
    request("10.0.0.1");
    clock.advance(Duration.ofMinutes(31));

    // the first has left the window; the other two have not
    assertDoesNotThrow(() -> request("10.0.0.1"));
    assertThrows(TooManyRequests.class, () -> request("10.0.0.1"));
  }

  private void request(String address)
  {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/anything");
    request.setRemoteAddr(address);

    limiter.preHandle(request, new MockHttpServletResponse(), new Object());
  }

  private static class TooManyRequests extends RuntimeException {}

  private static class MutableClock extends Clock
  {
    private Instant now = Instant.parse("2026-10-11T09:00:00Z");

    void advance(Duration duration)
    {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone()
    {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone)
    {
      return this;
    }

    @Override
    public Instant instant()
    {
      return now;
    }
  }
}
