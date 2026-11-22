package com.tn.service.logging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import net.logstash.logback.encoder.LogstashEncoder;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The lines a service logs, as the Logstash encoder its {@code logback-spring.xml} uses writes them, with its defaults.
 */
class JsonLines extends AppenderBase<ILoggingEvent>
{
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private final List<JsonNode> lines = new CopyOnWriteArrayList<>();
  private final LogstashEncoder encoder = new LogstashEncoder();

  static JsonLines capture()
  {
    LoggerContext loggerContext = (LoggerContext)LoggerFactory.getILoggerFactory();

    JsonLines jsonLines = new JsonLines();
    jsonLines.setContext(loggerContext);
    jsonLines.encoder.setContext(loggerContext);
    jsonLines.encoder.start();
    jsonLines.start();
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(jsonLines);

    return jsonLines;
  }

  void release()
  {
    ((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
    stop();
    encoder.stop();
  }

  List<JsonNode> lines()
  {
    return List.copyOf(lines);
  }

  List<JsonNode> linesFrom(Class<?> type)
  {
    return lines.stream().filter(line -> type.getName().equals(line.path("logger_name").asString())).toList();
  }

  @Override
  protected void append(ILoggingEvent event)
  {
    lines.add(JSON_MAPPER.readTree(new String(encoder.encode(event), StandardCharsets.UTF_8)));
  }
}
