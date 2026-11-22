package com.tn.service.logging;

import static java.util.Collections.enumeration;
import static java.util.Collections.list;

import static com.tn.service.logging.RequestBaggage.USER_ID;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Discards the {@code userId} an edge (a BFF) is sent, so no caller can make the edge's lines, or those of the services it calls, look
 * like someone else's: the entry in the W3C {@code baggage} header, and the {@code userId} header in which a remote baggage field is also
 * sent. Runs before the request's trace is read from its headers, so the caller's {@code userId} never reaches the trace or a log line.
 * A {@code userId} is then only what the edge sets itself, from a token it verifies.
 */
public class EdgeBaggageFilter extends OncePerRequestFilter
{
  static final String BAGGAGE = "baggage";

  private static final String MEMBER_SEPARATOR = ",";
  private static final String KEY_SEPARATOR = "=";
  private static final String PROPERTY_SEPARATOR = ";";

  @Override
  protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain)
    throws ServletException, IOException
  {
    filterChain.doFilter(new WithoutUserId(request), response);
  }

  private static class WithoutUserId extends HttpServletRequestWrapper
  {
    WithoutUserId(HttpServletRequest request)
    {
      super(request);
    }

    @Override
    public String getHeader(String name)
    {
      Enumeration<String> headers = getHeaders(name);

      return headers.hasMoreElements() ? headers.nextElement() : null;
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
      if (USER_ID.equalsIgnoreCase(name)) return enumeration(List.of());
      if (!BAGGAGE.equalsIgnoreCase(name)) return super.getHeaders(name);

      return enumeration(
        list(super.getHeaders(name)).stream()
          .map(WithoutUserId::withoutUserId)
          .filter(Objects::nonNull)
          .toList()
      );
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
      return enumeration(
        list(super.getHeaderNames()).stream()
          .filter(name -> !USER_ID.equalsIgnoreCase(name))
          .filter(name -> !BAGGAGE.equalsIgnoreCase(name) || getHeaders(name).hasMoreElements())
          .toList()
      );
    }

    private static String withoutUserId(String baggage)
    {
      String members = Arrays.stream(baggage.split(MEMBER_SEPARATOR))
        .filter(member -> !USER_ID.equalsIgnoreCase(key(member)))
        .collect(Collectors.joining(MEMBER_SEPARATOR));

      return members.isBlank() ? null : members;
    }

    private static String key(String member)
    {
      return member.split(PROPERTY_SEPARATOR, 2)[0].split(KEY_SEPARATOR, 2)[0].trim();
    }
  }
}
