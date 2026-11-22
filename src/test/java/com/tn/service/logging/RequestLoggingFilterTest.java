package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RequestLoggingFilterTest
{
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final String MASK = "****";

  private final RequestLoggingFilter filter = new RequestLoggingFilter();
  private final Logger logger = (Logger)LoggerFactory.getLogger(RequestLoggingFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final LogstashEncoder encoder = new LogstashEncoder();

  @BeforeEach
  void captureLines()
  {
    appender.start();
    logger.addAppender(appender);

    // as a consumer's logback-spring.xml has it: a field named token masked wherever it is
    MaskingJsonGeneratorDecorator masking = new MaskingJsonGeneratorDecorator();
    masking.setDefaultMask(MASK);
    masking.addPath("token");
    masking.start();
    encoder.setContext(logger.getLoggerContext());
    encoder.addDecorator(masking);
    encoder.start();
  }

  @AfterEach
  void releaseLines()
  {
    logger.detachAppender(appender);
    encoder.stop();
  }

  /** A request is logged once. */
  @Test
  void shouldLogTheMethodPathQueryStatusAndTimeOfARequest() throws Exception
  {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/spots");
    request.setQueryString("page=2&size=10");
    request.addHeader("X-Delegate-User-Token", "secret");
    request.setContent("{\"secret\":true}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(404);

    filter.doFilter(request, response, new MockFilterChain());

    ILoggingEvent line = assertOneLine();
    JsonNode json = json(line);
    assertEquals(Level.INFO, line.getLevel());
    assertEquals("Answered method=GET path=/v1/spots status=404", line.getFormattedMessage());
    assertEquals("GET", json.path("method").asString());
    assertEquals("/v1/spots", json.path("path").asString());
    assertEquals(404, json.path("status").asInt());
    assertTrue(json.path("durationMs").asLong() >= 0);
    assertEquals(JSON_MAPPER.readTree("{\"page\":\"2\",\"size\":\"10\"}"), json.path("query"));
    assertEquals(5, line.getArgumentArray().length);
    assertFalse(json.toString().contains("secret"));
  }

  @Test
  void shouldLogNoQueryWhenThereIsNone() throws Exception
  {
    filter.doFilter(new MockHttpServletRequest("POST", "/v1/posts"), new MockHttpServletResponse(), new MockFilterChain());

    ILoggingEvent line = assertOneLine();
    Map<String, String> fields = fields(line);
    assertEquals("/v1/posts", fields.get("path"));
    assertEquals("200", fields.get("status"));
    assertEquals(4, fields.size());
    assertTrue(json(line).path("query").isMissingNode());
  }

  @Test
  void shouldLogNoQueryWhenItIsEmpty() throws Exception
  {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/spots");
    request.setQueryString("&");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertTrue(json(assertOneLine()).path("query").isMissingNode());
  }

  @Test
  void shouldLogOneParameter() throws Exception
  {
    assertEquals("{\"page\":\"2\"}", query("page=2").toString());
  }

  @Test
  void shouldSkipAnEmptyParameter() throws Exception
  {
    assertEquals("{\"page\":\"2\",\"size\":\"10\"}", query("page=2&&size=10").toString());
  }

  @Test
  void shouldLogAParameterWithoutAValueAsEmpty() throws Exception
  {
    assertEquals("{\"flag\":\"\",\"page\":\"2\"}", query("flag&page=2").toString());
  }

  @Test
  void shouldLogARepeatedParameterAsAList() throws Exception
  {
    assertEquals("{\"tag\":[\"a\",\"b\"],\"page\":\"2\"}", query("tag=a&page=2&tag=b").toString());
  }

  @Test
  void shouldLogDecodedValues() throws Exception
  {
    assertEquals("{\"q\":\"name=park & ride\",\"café\":\"☕\"}", query("q=name%3Dpark+%26%20ride&caf%C3%A9=%E2%98%95").toString());
  }

  @Test
  void shouldLogAMalformedValueAsSent() throws Exception
  {
    assertEquals("{\"q\":\"100%\"}", query("q=100%").toString());
  }

  @Test
  void shouldMaskAParameterByName() throws Exception
  {
    JsonNode query = query("token=abc123&tag=a");

    assertEquals("{\"token\":\"" + MASK + "\",\"tag\":\"a\"}", query.toString());
    assertFalse(assertOneLine().getFormattedMessage().contains("abc123"));
  }

  @Test
  void shouldMaskARepeatedParameterByName() throws Exception
  {
    assertEquals("{\"token\":\"" + MASK + "\"}", query("token=abc123&token=def456").toString());
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

  private JsonNode query(String queryString) throws Exception
  {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/spots");
    request.setQueryString(queryString);

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    JsonNode json = json(assertOneLine());
    assertEquals("/v1/spots", json.path("path").asString());

    return json.path("query");
  }

  private JsonNode json(ILoggingEvent line)
  {
    return JSON_MAPPER.readTree(new String(encoder.encode(line), StandardCharsets.UTF_8));
  }

  private Map<String, String> fields(ILoggingEvent line)
  {
    return Arrays.stream(line.getArgumentArray())
      .map(Object::toString)
      .map(field -> field.split("=", 2))
      .collect(Collectors.toMap(field -> field[0], field -> field[1]));
  }
}
