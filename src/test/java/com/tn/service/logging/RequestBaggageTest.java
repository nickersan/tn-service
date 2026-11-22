package com.tn.service.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestBaggageTest
{
  private static final String NAME = "userId";
  private static final String VALUE = "891661201065939929";

  private final List<String> put = new ArrayList<>();
  private final BiConsumer<String, String> baggage = (name, value) -> put.add(name + "=" + value);

  @AfterEach
  void resetRequest()
  {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldPutIntoTheRequestsBaggage()
  {
    inRequestWith(baggage);

    RequestBaggage.put(NAME, VALUE);

    assertEquals(List.of(NAME + "=" + VALUE), put);
  }

  @Test
  void shouldIgnoreABlankOrMissingValue()
  {
    inRequestWith(baggage);

    RequestBaggage.put(NAME, null);
    RequestBaggage.put(NAME, " ");

    assertTrue(put.isEmpty());
  }

  @Test
  void shouldDoNothingOutsideARequest()
  {
    RequestBaggage.put(NAME, VALUE);

    assertTrue(put.isEmpty());
  }

  @Test
  void shouldDoNothingInARequestWithoutBaggage()
  {
    inRequestWith(null);

    RequestBaggage.put(NAME, VALUE);

    assertTrue(put.isEmpty());
  }

  @Test
  void shouldDoNothingWhenTheAttributeIsNotBaggage()
  {
    inRequestWith("not baggage");

    RequestBaggage.put(NAME, VALUE);

    assertTrue(put.isEmpty());
  }

  private void inRequestWith(Object attribute)
  {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (attribute != null) request.setAttribute(RequestBaggage.ATTRIBUTE, attribute);

    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
