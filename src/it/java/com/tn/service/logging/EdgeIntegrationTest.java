package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import com.tn.service.security.DelegateUserToken;

/**
 * An edge (a BFF) trusts no caller's user: it logs and sends on only a user it verifies itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = CorrelatedService.class, properties = "tn.logging.edge=true")
class EdgeIntegrationTest
{
  private static final String TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
  private static final String SUBJECT = "891661201065939929";

  private static StubServer stubServer;

  @LocalServerPort
  int port;

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

  /** A client asserts a user, by the legacy name as well. */
  @Test
  void shouldDiscardTheUserAClientAsserts() throws Exception
  {
    get(
      "traceparent", TRACEPARENT,
      "baggage", "user.id=someone,userId=someone-legacy,session.id=s-1,client.name=web",
      "user.id", "someone",
      "userId", "someone-legacy"
    );

    List<JsonNode> lines = lines();
    assertEquals(4, lines.size());
    lines.forEach(line -> assertTrue(line.path("user.id").isMissingNode(), line.toString()));
    lines.forEach(line -> assertTrue(line.path("userId").isMissingNode(), line.toString()));
    lines.forEach(line -> assertEquals("s-1", line.path("session.id").asString()));
    assertNull(stubServer.lastCall().getFirst("user.id"));
    assertNull(stubServer.lastCall().getFirst("userId"));
    assertFalse(stubServer.lastCall().getFirst("baggage").contains("someone"));
    assertTrue(stubServer.lastCall().getFirst("baggage").contains("client.name=web"));
  }

  /** An edge still logs the user from a token it verifies. */
  @Test
  void shouldLogTheUserFromATokenItVerifies() throws Exception
  {
    get("baggage", "user.id=someone,userId=someone-legacy", DelegateUserToken.HEADER, CorrelatedService.token(SUBJECT));

    assertEquals(List.of("", SUBJECT, SUBJECT, SUBJECT), lines().stream().map(line -> line.path("user.id").asString("")).toList());
    assertTrue(stubServer.lastCall().getFirst("baggage").contains("user.id=" + SUBJECT));
    assertFalse(stubServer.lastCall().getFirst("baggage").contains("someone"));
  }

  private void get(String... headers) throws IOException, InterruptedException
  {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/verified?to=" + URLEncoder.encode(stubServer.url(), StandardCharsets.UTF_8)))
      .headers(headers)
      .build();

    try (HttpClient httpClient = HttpClient.newHttpClient())
    {
      assertEquals(200, httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
    }
  }

  private List<JsonNode> lines()
  {
    return jsonLines.lines().stream()
      .filter(line -> List.of(CorrelatedService.Target.class.getName(), RequestLoggingFilter.class.getName()).contains(line.path("logger_name").asString()))
      .toList();
  }
}
