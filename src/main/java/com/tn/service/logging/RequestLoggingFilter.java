package com.tn.service.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes one INFO line for each request a service answers: its method, path, query parameters, status and the time taken, never its body
 * or headers. The query is a field of its own, an object of the parameters by name (decoded, a parameter given more than once as a list of
 * its values), absent when there is none, so a consumer masks a parameter by its name as it does any field; it is never in the message.
 * Runs inside the request's trace and writes the line once the request has been handled, so the line carries the trace and the baggage put
 * while handling it (the {@code userId} of a token verified on the way). Health probes and {@code /noop} are not logged.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter
{
  static final String FIELD_METHOD = "method";
  static final String FIELD_PATH = "path";
  static final String FIELD_QUERY = "query";
  static final String FIELD_STATUS = "status";
  static final String FIELD_DURATION = "durationMs";

  private static final List<String> UNLOGGED_PATHS = List.of("/noop");
  private static final List<String> UNLOGGED_PATH_PREFIXES = List.of("/actuator/health");
  private static final String PARAMETER_SEPARATOR = "&";
  private static final String VALUE_SEPARATOR = "=";
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
    List<Object> fields = new ArrayList<>(
      List.of(kv(FIELD_METHOD, request.getMethod()), kv(FIELD_PATH, request.getRequestURI()), kv(FIELD_STATUS, status), kv(FIELD_DURATION, durationMs))
    );
    Map<String, Object> query = query(request.getQueryString());
    if (!query.isEmpty()) fields.add(kv(FIELD_QUERY, query));

    // three placeholders: the duration and the query are fields of the line, never its message
    log.info("Answered {} {} {}", fields.toArray());
  }

  private String path(HttpServletRequest request)
  {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  private Map<String, Object> query(String queryString)
  {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    if (queryString != null)
    {
      for (String parameter : queryString.split(PARAMETER_SEPARATOR))
      {
        if (parameter.isEmpty()) continue;

        String[] nameAndValue = parameter.split(VALUE_SEPARATOR, 2);
        parameters.computeIfAbsent(decode(nameAndValue[0]), name -> new ArrayList<>()).add(nameAndValue.length > 1 ? decode(nameAndValue[1]) : "");
      }
    }

    Map<String, Object> query = new LinkedHashMap<>();
    parameters.forEach((name, values) -> query.put(name, values.size() == 1 ? values.getFirst() : List.copyOf(values)));

    return query;
  }

  private String decode(String value)
  {
    try
    {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e)
    {
      // a malformed escape is logged as it was sent rather than failing the request's line
      return value;
    }
  }
}
