package com.tn.service.logging;

import static java.util.Collections.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class EdgeBaggageFilterTest
{
  private static final String BAGGAGE = "baggage";
  private static final String TRACEPARENT = "traceparent";
  private static final String TRACE = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

  private final EdgeBaggageFilter filter = new EdgeBaggageFilter();

  @Test
  void shouldDiscardTheUserIdInTheBaggage() throws Exception
  {
    HttpServletRequest request = filtered(request().header(BAGGAGE, "session.id=s1, user.id=someone;prop=1 ,client.name=web"));

    assertEquals("session.id=s1,client.name=web", request.getHeader(BAGGAGE));
    assertEquals(List.of("session.id=s1,client.name=web"), list(request.getHeaders(BAGGAGE)));
  }

  @Test
  void shouldDiscardTheLegacyUserIdInTheBaggageAndHeaders() throws Exception
  {
    HttpServletRequest request = filtered(
      request()
        .header(BAGGAGE, "userId=someone, user.id=someone,session.id=s1")
        .header("userId", "someone")
        .header("user.id", "someone")
        .header(TRACEPARENT, TRACE)
    );

    assertEquals("session.id=s1", request.getHeader(BAGGAGE));
    assertNull(request.getHeader("userId"));
    assertNull(request.getHeader("user.id"));
    assertEquals(List.of(BAGGAGE, TRACEPARENT), list(request.getHeaderNames()));
  }

  @Test
  void shouldDiscardTheUserIdWhateverItsCase() throws Exception
  {
    HttpServletRequest request = filtered(request().header("Baggage", "USER.ID=someone,USERID=someone,client.name=web").header("User.Id", "someone").header("UserId", "someone"));

    assertEquals("client.name=web", request.getHeader(BAGGAGE));
    assertNull(request.getHeader("user.id"));
    assertEquals(List.of(), list(request.getHeaders("user.ID")));
    assertNull(request.getHeader("userId"));
    assertEquals(List.of(), list(request.getHeaders("userid")));
  }

  @Test
  void shouldDiscardABaggageHeaderThatHeldOnlyTheUserId() throws Exception
  {
    HttpServletRequest request = filtered(request().header(BAGGAGE, "user.id=someone").header(BAGGAGE, "client.name=web").header(TRACEPARENT, TRACE));

    assertEquals(List.of("client.name=web"), list(request.getHeaders(BAGGAGE)));

    HttpServletRequest onlyUserId = filtered(request().header(BAGGAGE, "user.id=someone").header("user.id", "someone").header(TRACEPARENT, TRACE));

    assertNull(onlyUserId.getHeader(BAGGAGE));
    assertEquals(List.of(TRACEPARENT), list(onlyUserId.getHeaderNames()));
  }

  @Test
  void shouldKeepEveryOtherHeader() throws Exception
  {
    HttpServletRequest request = filtered(request().header(TRACEPARENT, TRACE).header(BAGGAGE, "client.name=web"));

    assertEquals(TRACE, request.getHeader(TRACEPARENT));
    assertEquals(List.of(TRACE), list(request.getHeaders(TRACEPARENT)));
    assertEquals(List.of(TRACEPARENT, BAGGAGE), list(request.getHeaderNames()));
    assertNull(request.getHeader("other"));
  }

  private Builder request()
  {
    return new Builder();
  }

  private HttpServletRequest filtered(Builder builder) throws Exception
  {
    MockFilterChain filterChain = new MockFilterChain();
    filter.doFilter(builder.request, new MockHttpServletResponse(), filterChain);

    return (HttpServletRequest)filterChain.getRequest();
  }

  private static class Builder
  {
    final MockHttpServletRequest request = new MockHttpServletRequest();

    Builder header(String name, String value)
    {
      request.addHeader(name, value);
      return this;
    }
  }
}
