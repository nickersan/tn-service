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
 * Discards the {@code user.id} an edge (a BFF) is sent, so no caller can make the edge's lines, or those of the services it calls, look
 * like someone else's: the entry in the W3C {@code baggage} header, and the {@code user.id} header in which a remote baggage field is also
 * sent. Runs before the request's trace is read from its headers, so the caller's {@code user.id} never reaches the trace or a log line.
 * A {@code user.id} is then only what the edge sets itself, from a token it verifies.
 * <p>
 * The legacy {@code userId} (the name before tn-service 2.5.0) is discarded the same way, so a caller cannot smuggle one in to services
 * still on an older tn-service.
 */
public class EdgeBaggageFilter extends OncePerRequestFilter
{
  static final String BAGGAGE = "baggage";
  static final String LEGACY_USER_ID = "userId";
  static final List<String> DISCARDED = List.of(USER_ID, LEGACY_USER_ID);

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
      if (discarded(name)) return enumeration(List.of());
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
          .filter(name -> !discarded(name))
          .filter(name -> !BAGGAGE.equalsIgnoreCase(name) || getHeaders(name).hasMoreElements())
          .toList()
      );
    }

    private static String withoutUserId(String baggage)
    {
      String members = Arrays.stream(baggage.split(MEMBER_SEPARATOR))
        .filter(member -> !discarded(key(member)))
        .collect(Collectors.joining(MEMBER_SEPARATOR));

      return members.isBlank() ? null : members;
    }

    private static boolean discarded(String name)
    {
      return DISCARDED.stream().anyMatch(name::equalsIgnoreCase);
    }

    private static String key(String member)
    {
      return member.split(PROPERTY_SEPARATOR, 2)[0].split(KEY_SEPARATOR, 2)[0].trim();
    }
  }
}
