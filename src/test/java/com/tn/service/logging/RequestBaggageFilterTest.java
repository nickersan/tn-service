package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.BiConsumer;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBaggageFilterTest
{
  private final Tracer tracer = mock(Tracer.class);
  private final RequestBaggageFilter filter = new RequestBaggageFilter(tracer);
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test
  void shouldHoldTheRequestsBaggageWhileItIsHandledAndDropItAfter() throws Exception
  {
    BaggageInScope userId = baggageInScope();

    filter.doFilter(request, new MockHttpServletResponse(), (request, response) -> baggage(request).accept("userId", "1"));

    verify(userId).close();
    assertNull(request.getAttribute(RequestBaggage.ATTRIBUTE));
  }

  @Test
  void shouldDropTheRequestsBaggageWhenHandlingFails()
  {
    BaggageInScope userId = baggageInScope();

    assertThrows(
      ServletException.class,
      () -> filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (request, response) ->
        {
          baggage(request).accept("userId", "1");
          throw new ServletException("failed");
        }
      )
    );

    verify(userId).close();
    assertNull(request.getAttribute(RequestBaggage.ATTRIBUTE));
  }

  @Test
  void shouldPassTheRequestOn() throws Exception
  {
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), filterChain);

    assertInstanceOf(MockHttpServletRequest.class, filterChain.getRequest());
  }

  private BaggageInScope baggageInScope()
  {
    BaggageInScope baggageInScope = mock(BaggageInScope.class);
    when(tracer.currentSpan()).thenReturn(mock(Span.class));
    when(tracer.getAllBaggage()).thenReturn(Map.of());
    when(tracer.createBaggageInScope("userId", "1")).thenReturn(baggageInScope);

    return baggageInScope;
  }

  @SuppressWarnings("unchecked")
  private BiConsumer<String, String> baggage(ServletRequest request)
  {
    return (BiConsumer<String, String>)request.getAttribute(RequestBaggage.ATTRIBUTE);
  }
}
