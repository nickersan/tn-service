package com.tn.service.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Deliberately carries no cause: MVC matches exception handlers against the cause chain, so a jjwt cause could turn
 * this 401 into another status.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidAccessTokenException extends RuntimeException
{
  public InvalidAccessTokenException(String reason)
  {
    super("Invalid access token: " + reason);
  }
}
