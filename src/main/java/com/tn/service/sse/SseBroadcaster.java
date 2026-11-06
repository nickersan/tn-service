package com.tn.service.sse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds this instance's open server-sent event streams and fans each event out to all of them: a controller returns {@link #subscribe()}
 * and the service calls {@link #publish} as things happen.
 *
 * <p>A {@link SmartLifecycle} so that, on shutdown, it closes every open stream before the web server's graceful shutdown starts waiting
 * for in-flight requests: an open stream never finishes on its own, so otherwise shutdown always stalls for the full grace period. Clients
 * reconnect to another instance. It is a bean for the service to start and stop, not one this registers: a service has it as a bean, or
 * extends it to say what it publishes.
 */
@Slf4j
public class SseBroadcaster implements SmartLifecycle
{
  private static final long NO_TIMEOUT = 0L;
  private static final String CONNECTED_COMMENT = "connected";

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  private volatile boolean running;

  @Override
  public void start()
  {
    running = true;
  }

  @Override
  public void stop()
  {
    running = false;
    emitters.forEach(SseEmitter::complete);
    emitters.clear();
  }

  @Override
  public boolean isRunning()
  {
    return running;
  }

  /** @return how many streams are open on this instance */
  public int subscribers()
  {
    return emitters.size();
  }

  /**
   * The returned stream opens with a comment, so the subscriber's response headers are sent (and it knows it's subscribed) straight away
   * rather than when the first event happens to arrive.
   */
  public SseEmitter subscribe()
  {
    SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));
    emitters.add(emitter);

    try
    {
      emitter.send(SseEmitter.event().comment(CONNECTED_COMMENT));
    }
    catch (IOException e)
    {
      emitters.remove(emitter);
      emitter.completeWithError(e);
    }

    return emitter;
  }

  /** Sends the event, named, with the data as JSON, to every open stream; one that can no longer be written to is dropped. */
  public void publish(String eventName, Object data)
  {
    for (SseEmitter emitter : emitters)
    {
      try
      {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
      }
      catch (IOException | IllegalStateException e)
      {
        emitters.remove(emitter);
        log.debug("Dropped event subscriber", e);
      }
    }
  }
}
