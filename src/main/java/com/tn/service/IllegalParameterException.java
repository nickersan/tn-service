package com.tn.service;

public class IllegalParameterException extends RuntimeException
{
  public IllegalParameterException(String message)
  {
    super(message);
  }
}
