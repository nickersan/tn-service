package com.tn.service.logging;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;

class BaggageScopesTest
{
  private static final String USER_ID = "user.id";
  private static final String SESSION_ID = "session.id";

  private final Tracer tracer = mock(Tracer.class);

  @Test
  void shouldPutBaggageInAScopeOfItsOwnAndCloseTheScopesLastFirst()
  {
    BaggageInScope userId = mock(BaggageInScope.class);
    BaggageInScope sessionId = mock(BaggageInScope.class);
    when(tracer.currentSpan()).thenReturn(mock(Span.class));
    when(tracer.getAllBaggage()).thenReturn(Map.of());
    when(tracer.createBaggageInScope(USER_ID, "1")).thenReturn(userId);
    when(tracer.createBaggageInScope(SESSION_ID, "s")).thenReturn(sessionId);

    try (BaggageScopes baggageScopes = new BaggageScopes(tracer))
    {
      baggageScopes.accept(USER_ID, "1");
      baggageScopes.accept(SESSION_ID, "s");
    }

    InOrder closed = inOrder(sessionId, userId);
    closed.verify(sessionId).close();
    closed.verify(userId).close();
  }

  @Test
  void shouldNotPutBaggageOutsideASpan()
  {
    try (BaggageScopes baggageScopes = new BaggageScopes(tracer))
    {
      baggageScopes.accept(USER_ID, "1");
    }

    verify(tracer, never()).createBaggageInScope(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
  }

  @Test
  void shouldNotPutBaggageThatIsAlreadySet()
  {
    when(tracer.currentSpan()).thenReturn(mock(Span.class));
    when(tracer.getAllBaggage()).thenReturn(Map.of(USER_ID, "1"));

    try (BaggageScopes baggageScopes = new BaggageScopes(tracer))
    {
      baggageScopes.accept(USER_ID, "1");
    }

    verify(tracer, never()).createBaggageInScope(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
  }
}
