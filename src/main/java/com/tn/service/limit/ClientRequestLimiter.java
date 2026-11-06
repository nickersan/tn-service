package com.tn.service.limit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limits how many requests one client address can make to the paths it is registered for in a time window, whichever of the things the paths
 * act on it names: it stops a single source, where a limit on each thing cannot. A request over the limit is refused with the exception the
 * service gives, so that it is answered as the service answers that kind of refusal, and does not count against the client.
 *
 * <p>It counts per instance, so across n instances a client can get up to n times the limit - enough to bound abuse, and the edge is where a
 * precise limit belongs. Behind a proxy, set {@code server.forward-headers-strategy} so the address is the caller's rather than the proxy's.
 */
public class ClientRequestLimiter implements HandlerInterceptor
{
  private final int maxRequests;
  private final Duration window;
  private final Clock clock;
  private final Supplier<? extends RuntimeException> refusal;
  private final Map<String, Requests> clients = new ConcurrentHashMap<>();

  /**
   * @param maxRequests the most requests a client may make in the window
   * @param window      how long a request counts against the client
   * @param refusal     makes what is thrown for a request over the limit
   */
  public ClientRequestLimiter(int maxRequests, Duration window, Clock clock, Supplier<? extends RuntimeException> refusal)
  {
    this.maxRequests = maxRequests;
    this.window = window;
    this.clock = clock;
    this.refusal = refusal;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
  {
    long now = clock.millis();
    long since = now - window.toMillis();

    // forget clients that have been quiet for a whole window, so the map doesn't grow without bound
    clients.values().removeIf(requests -> requests.quietSince(since));

    if (!clients.computeIfAbsent(request.getRemoteAddr(), client -> new Requests()).tryAdd(now, since, maxRequests)) throw refusal.get();

    return true;
  }

  private static class Requests
  {
    private final Deque<Long> times = new ArrayDeque<>();

    synchronized boolean tryAdd(long now, long since, int max)
    {
      while (!times.isEmpty() && times.peekFirst() <= since) times.removeFirst();
      if (times.size() >= max) return false;

      times.addLast(now);

      return true;
    }

    synchronized boolean quietSince(long since)
    {
      return times.isEmpty() || times.peekLast() <= since;
    }
  }
}
