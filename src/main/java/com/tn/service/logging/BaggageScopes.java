package com.tn.service.logging;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;

/**
 * The baggage put for one request, each field in a scope of its own that stays open until the request ends and is then closed, last
 * first, so the trace's context is left as the request found it.
 */
@RequiredArgsConstructor
class BaggageScopes implements BiConsumer<String, String>, AutoCloseable
{
  private final Tracer tracer;
  private final Deque<BaggageInScope> scopes = new ArrayDeque<>();

  @Override
  public void accept(String name, String value)
  {
    if (tracer.currentSpan() == null || value.equals(tracer.getAllBaggage().get(name))) return;

    scopes.push(tracer.createBaggageInScope(name, value));
  }

  @Override
  public void close()
  {
    while (!scopes.isEmpty()) scopes.pop().close();
  }
}
