package com.tn.service.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseBroadcasterTest
{
  private final SseBroadcaster broadcaster = new SseBroadcaster();
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Events(broadcaster)).build();

  @RestController
  static class Events
  {
    private final SseBroadcaster broadcaster;

    Events(SseBroadcaster broadcaster)
    {
      this.broadcaster = broadcaster;
    }

    @GetMapping("/events")
    SseEmitter events()
    {
      return broadcaster.subscribe();
    }
  }

  @Test
  void shouldOpenAStreamWithACommentSoTheSubscriberKnowsItIsSubscribed() throws Exception
  {
    MvcResult stream = subscribe();

    assertTrue(stream.getResponse().getContentAsString().startsWith(":connected"));
    assertTrue(stream.getResponse().getContentType().startsWith("text/event-stream"));
    assertEquals(1, broadcaster.subscribers());
  }

  @Test
  void shouldSendAnEventToEveryOpenStreamNamedAndAsJson() throws Exception
  {
    MvcResult first = subscribe();
    MvcResult second = subscribe();

    broadcaster.publish("spot.updated", Map.of("id", 7));

    for (MvcResult stream : new MvcResult[] {first, second})
    {
      String content = stream.getResponse().getContentAsString();

      assertTrue(content.contains("event:spot.updated"), content);
      assertTrue(content.contains("data:{\"id\":7}"), content);
    }
  }

  @Test
  void shouldSendAnEventToNoOneWhenNobodyIsSubscribed()
  {
    broadcaster.publish("spot.updated", Map.of("id", 7));

    assertEquals(0, broadcaster.subscribers());
  }

  @Test
  void shouldCloseEveryStreamAndForgetThemWhenStopped() throws Exception
  {
    MvcResult first = subscribe();
    MvcResult second = subscribe();

    broadcaster.start();
    broadcaster.stop();

    assertEquals(0, broadcaster.subscribers());
    for (MvcResult stream : new MvcResult[] {first, second})
    {
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(stream)).andExpect(request().asyncNotStarted());
    }
  }

  @Test
  void shouldNotSendToAStreamThatWasClosedByStopping() throws Exception
  {
    MvcResult stream = subscribe();
    broadcaster.stop();
    String before = stream.getResponse().getContentAsString();

    broadcaster.publish("spot.updated", Map.of("id", 7));

    assertEquals(before, stream.getResponse().getContentAsString());
  }

  @Test
  void shouldSayWhetherItIsRunning()
  {
    assertFalse(broadcaster.isRunning());

    broadcaster.start();
    assertTrue(broadcaster.isRunning());

    broadcaster.stop();
    assertFalse(broadcaster.isRunning());
  }

  private MvcResult subscribe() throws Exception
  {
    return mvc.perform(get("/events")).andExpect(request().asyncStarted()).andReturn();
  }
}
