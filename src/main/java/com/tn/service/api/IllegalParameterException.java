package com.tn.service.api;

public class IllegalParameterException extends RuntimeException
{
  public IllegalParameterException(String message)
  {
    super(message);
  }
}
