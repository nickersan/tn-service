package com.tn.service.logging;

import java.util.function.BiConsumer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Baggage for the rest of the request being handled: the field is put on every later log line for the request and sent on with every
 * call the service makes while handling it.
 * <p>
 * Does nothing outside a request, or where the service has no tracing, so code that puts baggage needs neither. The baggage is held by
 * {@link RequestBaggageFilter} and dropped when the request ends.
 * <p>
 * It is for log lines only: never identify or authorize anyone on it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestBaggage
{
  /**
   * The user a request is for, as a dotted name so it never clashes with a {@code userId} a service logs itself.
   */
  public static final String USER_ID = "user.id";

  /**
   * The session a request is part of, as a dotted name so it never clashes with a {@code sessionId} a service logs itself.
   */
  public static final String SESSION_ID = "session.id";

  /**
   * The client (web, android...) a request came from, as a dotted name so it never clashes with a {@code client} a service logs itself.
   */
  public static final String CLIENT = "client.name";

  static final String ATTRIBUTE = RequestBaggage.class.getName();

  /**
   * Puts {@code name} in the baggage, as {@code value}, for the rest of the request; a blank value is ignored.
   */
  public static void put(String name, String value)
  {
    if (value == null || value.isBlank()) return;

    BiConsumer<String, String> baggage = baggage();
    if (baggage != null) baggage.accept(name, value);
  }

  @SuppressWarnings("unchecked")
  private static BiConsumer<String, String> baggage()
  {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes == null) return null;

    return requestAttributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof BiConsumer<?, ?> baggage
      ? (BiConsumer<String, String>)baggage
      : null;
  }
}
