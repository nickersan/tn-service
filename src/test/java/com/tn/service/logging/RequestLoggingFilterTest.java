package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest
{
  private final RequestLoggingFilter filter = new RequestLoggingFilter();
  private final Logger logger = (Logger)LoggerFactory.getLogger(RequestLoggingFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void captureLines()
  {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void releaseLines()
  {
    logger.detachAppender(appender);
  }

  @Test
  void shouldLogTheMethodPathQueryStatusAndTimeOfARequest() throws Exception
  {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/spots");
    request.setQueryString("q=name%3Dpark");
    request.addHeader("X-Delegate-User-Token", "secret");
    request.setContent("{\"secret\":true}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(404);

    filter.doFilter(request, response, new MockFilterChain());

    ILoggingEvent line = assertOneLine();
    Map<String, String> fields = fields(line);
    assertEquals(Level.INFO, line.getLevel());
    assertEquals("GET", fields.get("method"));
    assertEquals("/v1/spots?q=name%3Dpark", fields.get("path"));
    assertEquals("404", fields.get("status"));
    assertTrue(Long.parseLong(fields.get("durationMs")) >= 0);
    assertEquals(4, fields.size());
    assertFalse(line.getFormattedMessage().contains("secret"));
  }

  @Test
  void shouldLogAPathWithoutAQuery() throws Exception
  {
    filter.doFilter(new MockHttpServletRequest("POST", "/v1/posts"), new MockHttpServletResponse(), new MockFilterChain());

    Map<String, String> fields = fields(assertOneLine());
    assertEquals("/v1/posts", fields.get("path"));
    assertEquals("200", fields.get("status"));
  }

  @Test
  void shouldLogAFailedRequestAsAServerError()
  {
    assertThrows(
      ServletException.class,
      () -> filter.doFilter(
        new MockHttpServletRequest("GET", "/v1/spots"),
        new MockHttpServletResponse(),
        (request, response) ->
        {
          throw new ServletException("failed");
        }
      )
    );

    assertEquals("500", fields(assertOneLine()).get("status"));
  }

  @Test
  void shouldNotLogProbesOrNoop() throws Exception
  {
    for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/noop"))
    {
      filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new MockFilterChain());
    }

    assertTrue(appender.list.isEmpty());
  }

  @Test
  void shouldLogAPathThatOnlyStartsLikeAProbe() throws Exception
  {
    filter.doFilter(new MockHttpServletRequest("GET", "/actuator/healthy"), new MockHttpServletResponse(), new MockFilterChain());
    filter.doFilter(new MockHttpServletRequest("GET", "/noop/other"), new MockHttpServletResponse(), new MockFilterChain());

    assertEquals(2, appender.list.size());
  }

  @Test
  void shouldNotLogProbesUnderAContextPath() throws Exception
  {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/spot/actuator/health");
    request.setContextPath("/spot");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertTrue(appender.list.isEmpty());
  }

  private ILoggingEvent assertOneLine()
  {
    assertEquals(1, appender.list.size());

    return appender.list.getFirst();
  }

  private Map<String, String> fields(ILoggingEvent line)
  {
    return Arrays.stream(line.getArgumentArray())
      .map(Object::toString)
      .map(field -> field.split("=", 2))
      .collect(Collectors.toMap(field -> field[0], field -> field[1]));
  }
}
