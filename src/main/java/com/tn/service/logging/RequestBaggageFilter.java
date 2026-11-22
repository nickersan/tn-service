package com.tn.service.logging;

import java.io.IOException;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Holds the {@link RequestBaggage} put while a request is handled, and drops it when the request ends. Runs inside the request's trace,
 * right after the filter that starts it.
 */
@RequiredArgsConstructor
public class RequestBaggageFilter extends OncePerRequestFilter
{
  private final Tracer tracer;

  @Override
  protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain)
    throws ServletException, IOException
  {
    try (BaggageScopes baggageScopes = new BaggageScopes(tracer))
    {
      request.setAttribute(RequestBaggage.ATTRIBUTE, baggageScopes);
      filterChain.doFilter(request, response);
    }
    finally
    {
      request.removeAttribute(RequestBaggage.ATTRIBUTE);
    }
  }
}
