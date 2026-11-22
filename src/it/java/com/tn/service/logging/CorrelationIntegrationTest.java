package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;

import com.tn.service.security.DelegateUserToken;

/**
 * A service's log lines name the action they are part of, who it is for and the service that wrote them; and the service sends the trace
 * and baggage on with every call it makes.
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  classes = CorrelatedService.class,
  properties = "spring.info.build.location=classpath:com/tn/service/logging/build-info.properties"
)
class CorrelationIntegrationTest
{
  static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  static final String PARENT_SPAN_ID = "b7ad6b7169203331";
  static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01";
  static final String SUBJECT = "891661201065939929";

  private static StubServer stubServer;

  @LocalServerPort
  int port;

  @Autowired
  ApplicationContext applicationContext;

  private JsonLines jsonLines;

  @BeforeAll
  static void startOtherService() throws IOException
  {
    stubServer = new StubServer();
  }

  @AfterAll
  static void stopOtherService()
  {
    stubServer.close();
  }

  @BeforeEach
  void captureLines()
  {
    jsonLines = JsonLines.capture();
  }

  @AfterEach
  void releaseLines()
  {
    jsonLines.release();
  }

  /** A request that carries a trace. */
  @Test
  void shouldLogTheTraceOfARequestThatCarriesATrace() throws Exception
  {
    get("/logged", "traceparent", TRACEPARENT);

    List<JsonNode> lines = requestLines();
    assertEquals(3, lines.size());
    lines.forEach(line -> assertEquals(TRACE_ID, line.path("traceId").asString()));
    lines.forEach(line -> assertNotEquals(PARENT_SPAN_ID, line.path("spanId").asString()));
  }

  /** A request that carries none. */
  @Test
  void shouldLogOneNewTraceForARequestThatCarriesNone() throws Exception
  {
    get("/logged");
    get("/logged");

    List<JsonNode> lines = requestLines();
    assertEquals(6, lines.size());
    Set<String> traceIds = lines.stream().map(line -> line.path("traceId").asString()).collect(Collectors.toSet());
    assertEquals(2, traceIds.size());
    traceIds.forEach(traceId -> assertTrue(traceId.matches("[0-9a-f]{32}") && !traceId.matches("0+"), traceId));
    assertEquals(1, lines.subList(0, 3).stream().map(line -> line.path("traceId").asString()).distinct().count());
  }

  /** The trace is sent on. */
  @Test
  void shouldSendTheTraceOnWhenItCallsAnotherService() throws Exception
  {
    get("/called?to=" + encode(stubServer.url()), "traceparent", TRACEPARENT);

    String traceparent = stubServer.lastCall().getFirst("traceparent");
    assertTrue(traceparent.startsWith("00-" + TRACE_ID + "-"), traceparent);
    assertFalse(traceparent.contains(PARENT_SPAN_ID));
    requestLines().forEach(line -> assertEquals(TRACE_ID, line.path("traceId").asString()));
  }

  /** Session and client carried on. */
  @Test
  void shouldLogAndSendOnTheSessionAndClientItIsSent() throws Exception
  {
    get("/called?to=" + encode(stubServer.url()), "traceparent", TRACEPARENT, "baggage", "sessionId=s-1,client=web");

    List<JsonNode> lines = requestLines();
    lines.forEach(line -> assertEquals("s-1", line.path("sessionId").asString()));
    lines.forEach(line -> assertEquals("web", line.path("client").asString()));
    assertBaggageSent("sessionId=s-1", "client=web");
  }

  /** The user from a verified token. */
  @Test
  void shouldLogAndSendOnTheUserFromAVerifiedToken() throws Exception
  {
    get("/verified?to=" + encode(stubServer.url()), "traceparent", TRACEPARENT, DelegateUserToken.HEADER, CorrelatedService.token(SUBJECT));

    assertEquals(List.of("", SUBJECT, SUBJECT), userIds(jsonLines.linesFrom(CorrelatedService.Target.class)));
    assertEquals(SUBJECT, requestLine().path("userId").asString());
    assertBaggageSent("userId=" + SUBJECT);
  }

  /** A verified token wins over a sent user. */
  @Test
  void shouldLogAndSendOnTheVerifiedUserOverOneItIsSent() throws Exception
  {
    get(
      "/verified?to=" + encode(stubServer.url()),
      "traceparent", TRACEPARENT,
      "baggage", "userId=someone-else",
      DelegateUserToken.HEADER, CorrelatedService.token(SUBJECT)
    );

    assertEquals(List.of("someone-else", SUBJECT, SUBJECT), userIds(jsonLines.linesFrom(CorrelatedService.Target.class)));
    assertEquals(SUBJECT, requestLine().path("userId").asString());
    assertBaggageSent("userId=" + SUBJECT);
    assertFalse(stubServer.lastCall().getFirst("baggage").contains("someone-else"));
  }

  /** A service that is trusted with the user it is sent (not an edge) logs it. */
  @Test
  void shouldLogTheUserItIsSentWhenItIsNotAnEdge() throws Exception
  {
    get("/logged", "baggage", "userId=" + SUBJECT);

    requestLines().forEach(line -> assertEquals(SUBJECT, line.path("userId").asString()));
  }

  /** Nothing is carried from one request to the next on the same thread. */
  @Test
  void shouldNotCarryTheUserIntoTheNextRequest() throws Exception
  {
    for (int i = 0; i < 20; i++)
    {
      get("/verified?to=" + encode(stubServer.url()), DelegateUserToken.HEADER, CorrelatedService.token(SUBJECT));
      get("/logged");
    }

    jsonLines.linesFrom(CorrelatedService.Target.class).stream()
      .filter(line -> line.path("message").asString().equals("first"))
      .forEach(line -> assertTrue(line.path("userId").isMissingNode(), line.toString()));
  }

  /** A request is logged once. */
  @Test
  void shouldLogARequestOnceWithItsMethodPathQueryStatusAndTime() throws Exception
  {
    get("/logged?page=2&tag=a&tag=b", "traceparent", TRACEPARENT, "baggage", "sessionId=s-1,client=web", "X-Secret", "do-not-log");

    JsonNode line = requestLine();
    assertEquals("INFO", line.path("level").asString());
    assertEquals("GET", line.path("method").asString());
    assertEquals("/logged", line.path("path").asString());
    assertEquals("2", line.path("query").path("page").asString());
    assertEquals(List.of("a", "b"), line.path("query").path("tag").valueStream().map(JsonNode::asString).toList());
    assertEquals(200, line.path("status").asInt());
    assertTrue(line.path("durationMs").isNumber());
    assertEquals(TRACE_ID, line.path("traceId").asString());
    assertEquals("s-1", line.path("sessionId").asString());
    assertEquals("web", line.path("client").asString());
    assertFalse(line.toString().contains("do-not-log"));
  }

  /** Probes are not logged. */
  @Test
  void shouldNotLogProbesOrNoop() throws Exception
  {
    assertEquals(200, get("/actuator/health/liveness").statusCode());
    assertEquals(200, get("/actuator/health/readiness").statusCode());
    assertEquals(200, get("/actuator/health").statusCode());
    assertEquals(200, get("/noop").statusCode());

    assertTrue(jsonLines.linesFrom(RequestLoggingFilter.class).isEmpty());
  }

  /** Name and version on every line. */
  @Test
  void shouldNameTheServiceAndItsVersionOnEveryLine() throws Exception
  {
    get("/verified?to=" + encode(stubServer.url()), DelegateUserToken.HEADER, CorrelatedService.token(SUBJECT));
    org.slf4j.LoggerFactory.getLogger(getClass()).info("outside a request");

    List<JsonNode> lines = jsonLines.lines();
    assertTrue(lines.size() >= 5);
    lines.forEach(line -> assertEquals("tn-correlated-service", line.path("service.name").asString(), line.toString()));
    lines.forEach(line -> assertEquals("3.1.4", line.path("service.version").asString(), line.toString()));
  }

  /** No spans are exported. */
  @Test
  void shouldExportNoSpans()
  {
    assertTrue(applicationContext.getBeansOfType(SpanExporter.class).isEmpty());
  }

  private HttpResponse<Void> get(String path, String... headers) throws IOException, InterruptedException
  {
    stubServer.clear();

    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (headers.length > 0) request.headers(headers);

    try (HttpClient httpClient = HttpClient.newHttpClient())
    {
      return httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }
  }

  private List<JsonNode> requestLines()
  {
    return jsonLines.lines().stream()
      .filter(line -> List.of(CorrelatedService.Target.class.getName(), RequestLoggingFilter.class.getName()).contains(line.path("logger_name").asString()))
      .toList();
  }

  private JsonNode requestLine()
  {
    List<JsonNode> lines = jsonLines.linesFrom(RequestLoggingFilter.class);
    assertEquals(1, lines.size());

    return lines.getFirst();
  }

  private List<String> userIds(List<JsonNode> lines)
  {
    return lines.stream().map(line -> line.path("userId").asString("")).toList();
  }

  private void assertBaggageSent(String... entries)
  {
    // a member may carry properties after its value (the user's, put here, says it propagates without limit)
    List<String> sent = Stream.of(stubServer.lastCall().getFirst("baggage").split(",")).map(member -> member.split(";")[0].trim()).toList();
    for (String entry : entries) assertTrue(sent.contains(entry), sent + " has no " + entry);
  }

  private String encode(String value)
  {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
