package com.tn.service.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Says why a request was refused: one WARN line for each of a service's refusals, carrying the status, the exception's type and the
 * reason. A refusal is an exception a service marks with a 4xx {@link ResponseStatus} (the reason is its message), or a
 * {@link ResponseStatusException} with a 4xx status (the reason is the one it was given). It only logs, resolving nothing, so the refusal
 * is answered by the resolvers after it, with the same status and body as before; the line is written while the request is handled, so it
 * carries the same trace and baggage as the request's line ({@link RequestLoggingFilter}) and is matched to it by them. WARN, as the
 * logging standard has it for a request refused for a reason the caller can act on. Never the request's body: a refusal's message names a
 * profile, a size or an id, nothing more. A server error, and an exception with no status, are left to be logged where they are handled.
 */
@Slf4j
public class RefusalLogger implements HandlerExceptionResolver, Ordered
{
  static final String FIELD_STATUS = "status";
  static final String FIELD_REASON = "reason";
  static final String FIELD_EXCEPTION = "exception";

  @Override
  public ModelAndView resolveException(
    @Nonnull HttpServletRequest request,
    @Nonnull HttpServletResponse response,
    Object handler,
    @Nonnull Exception exception
  )
  {
    if (exception instanceof ResponseStatusException responseStatusException)
    {
      if (responseStatusException.getStatusCode().is4xxClientError())
      {
        logRefusal(responseStatusException.getStatusCode().value(), responseStatusException.getReason(), exception);
      }
    }
    else
    {
      ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
      if (responseStatus != null && responseStatus.code().is4xxClientError())
      {
        logRefusal(responseStatus.code().value(), exception.getMessage(), exception);
      }
    }

    // left to the resolvers after this one, which answer it as they always have
    return null;
  }

  @Override
  public int getOrder()
  {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private void logRefusal(int status, String reason, Exception exception)
  {
    // two placeholders: the exception's type is a field of the line, never its message
    log.warn("Refused {} {}", kv(FIELD_STATUS, status), kv(FIELD_REASON, reason), kv(FIELD_EXCEPTION, exception.getClass().getName()));
  }
}
