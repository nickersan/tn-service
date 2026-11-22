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
    HttpServletRequest request = filtered(request().header(BAGGAGE, "sessionId=s1, userId=someone;prop=1 ,client=web"));

    assertEquals("sessionId=s1,client=web", request.getHeader(BAGGAGE));
    assertEquals(List.of("sessionId=s1,client=web"), list(request.getHeaders(BAGGAGE)));
  }

  @Test
  void shouldDiscardTheUserIdWhateverItsCase() throws Exception
  {
    HttpServletRequest request = filtered(request().header("Baggage", "USERID=someone,client=web").header("UserId", "someone"));

    assertEquals("client=web", request.getHeader(BAGGAGE));
    assertNull(request.getHeader("userId"));
    assertEquals(List.of(), list(request.getHeaders("userid")));
  }

  @Test
  void shouldDiscardABaggageHeaderThatHeldOnlyTheUserId() throws Exception
  {
    HttpServletRequest request = filtered(request().header(BAGGAGE, "userId=someone").header(BAGGAGE, "client=web").header(TRACEPARENT, TRACE));

    assertEquals(List.of("client=web"), list(request.getHeaders(BAGGAGE)));

    HttpServletRequest onlyUserId = filtered(request().header(BAGGAGE, "userId=someone").header("userId", "someone").header(TRACEPARENT, TRACE));

    assertNull(onlyUserId.getHeader(BAGGAGE));
    assertEquals(List.of(TRACEPARENT), list(onlyUserId.getHeaderNames()));
  }

  @Test
  void shouldKeepEveryOtherHeader() throws Exception
  {
    HttpServletRequest request = filtered(request().header(TRACEPARENT, TRACE).header(BAGGAGE, "client=web"));

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
