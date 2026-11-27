package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

class RefusalLoggerTest
{
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final RefusalLogger refusalLogger = new RefusalLogger();
  private final Logger logger = (Logger)LoggerFactory.getLogger(RefusalLogger.class);
  private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

  @BeforeEach
  void captureLines()
  {
    lines.start();
    logger.addAppender(lines);
  }

  @AfterEach
  void releaseLines()
  {
    logger.detachAppender(lines);
    lines.stop();
  }

  @ParameterizedTest
  @MethodSource("refusals")
  void shouldLogWhyARequestWasRefused(Exception refusal, int status, String reason)
  {
    assertNull(refusalLogger.resolveException(request, response, null, refusal));

    assertEquals(1, lines.list.size(), lines.list.toString());
    ILoggingEvent line = lines.list.getFirst();
    assertEquals(Level.WARN, line.getLevel());
    assertEquals("Refused status=" + status + " reason=" + reason, line.getFormattedMessage());
    assertEquals(
      List.of("status=" + status, "reason=" + reason, "exception=" + refusal.getClass().getName()),
      Arrays.stream(line.getArgumentArray()).map(String::valueOf).toList()
    );
    verifyNoInteractions(request, response);
  }

  @ParameterizedTest
  @MethodSource("unrefused")
  void shouldNotLogAnExceptionThatIsNotARefusal(Exception exception)
  {
    assertNull(refusalLogger.resolveException(request, response, null, exception));

    assertTrue(lines.list.isEmpty(), lines.list.toString());
    verifyNoInteractions(request, response);
  }

  @Test
  void shouldLogBeforeAnyOtherResolver()
  {
    assertEquals(Ordered.HIGHEST_PRECEDENCE, refusalLogger.getOrder());
  }

  private static Stream<Arguments> refusals()
  {
    return Stream.of(
      Arguments.arguments(new InvalidException("No such upload profile: post-video"), 400, "No such upload profile: post-video"),
      Arguments.arguments(new NotFoundException("No such thing: 1"), 404, "No such thing: 1"),
      Arguments.arguments(new NoSuchThingException(), 404, "No such thing: 2"),
      Arguments.arguments(new TooManyException(), 429, "Too many: try again later"),
      Arguments.arguments(new ResponseStatusException(HttpStatus.CONFLICT, "Already taken: 3"), 409, "Already taken: 3"),
      Arguments.arguments(new ResponseStatusException(HttpStatus.FORBIDDEN), 403, null)
    );
  }

  private static Stream<Arguments> unrefused()
  {
    return Stream.of(
      Arguments.arguments(new IllegalStateException("Broken: everything")),
      Arguments.arguments(new UnavailableException()),
      Arguments.arguments(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream: down"))
    );
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  private static class InvalidException extends RuntimeException
  {
    InvalidException(String message)
    {
      super(message);
    }
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  private static class NotFoundException extends RuntimeException
  {
    NotFoundException(String message)
    {
      super(message);
    }
  }

  /** Its status is inherited. */
  private static class NoSuchThingException extends NotFoundException
  {
    NoSuchThingException()
    {
      super("No such thing: 2");
    }
  }

  /** A checked exception, given its status by code. */
  @ResponseStatus(code = HttpStatus.TOO_MANY_REQUESTS)
  private static class TooManyException extends Exception
  {
    TooManyException()
    {
      super("Too many: try again later");
    }
  }

  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  private static class UnavailableException extends RuntimeException
  {
    UnavailableException()
    {
      super("Unavailable: now");
    }
  }
}
