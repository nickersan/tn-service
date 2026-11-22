package com.tn.service.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.io.IOException;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes one INFO line for each request a service answers: its method, path and query, status and the time taken, never its body or
 * headers. Runs inside the request's trace and writes the line once the request has been handled, so the line carries the trace and the
 * baggage put while handling it (the {@code userId} of a token verified on the way). Health probes and {@code /noop} are not logged.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter
{
  static final String FIELD_METHOD = "method";
  static final String FIELD_PATH = "path";
  static final String FIELD_STATUS = "status";
  static final String FIELD_DURATION = "durationMs";

  private static final List<String> UNLOGGED_PATHS = List.of("/noop");
  private static final List<String> UNLOGGED_PATH_PREFIXES = List.of("/actuator/health");
  private static final String QUERY_SEPARATOR = "?";
  private static final int STATUS_FAILED = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

  @Override
  protected boolean shouldNotFilter(@Nonnull HttpServletRequest request)
  {
    String path = path(request);

    return UNLOGGED_PATHS.contains(path) || UNLOGGED_PATH_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
  }

  @Override
  protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain)
    throws ServletException, IOException
  {
    long start = System.nanoTime();
    boolean failed = true;

    try
    {
      filterChain.doFilter(request, response);
      failed = false;
    }
    finally
    {
      logRequest(request, failed ? STATUS_FAILED : response.getStatus(), (System.nanoTime() - start) / 1_000_000);
    }
  }

  private void logRequest(HttpServletRequest request, int status, long durationMs)
  {
    log.info(
      "Answered {} {} {}",
      kv(FIELD_METHOD, request.getMethod()),
      kv(FIELD_PATH, pathAndQuery(request)),
      kv(FIELD_STATUS, status),
      kv(FIELD_DURATION, durationMs)
    );
  }

  private String path(HttpServletRequest request)
  {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  private String pathAndQuery(HttpServletRequest request)
  {
    return request.getQueryString() == null ? request.getRequestURI() : request.getRequestURI() + QUERY_SEPARATOR + request.getQueryString();
  }
}
