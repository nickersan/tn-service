package com.tn.service.logging;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

/**
 * Another service, which records the headers of each call made to it.
 */
class StubServer implements AutoCloseable
{
  private final HttpServer httpServer;
  private final List<Headers> calls = new CopyOnWriteArrayList<>();

  StubServer() throws IOException
  {
    httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    httpServer.createContext(
      "/",
      exchange ->
      {
        calls.add(exchange.getRequestHeaders());
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
      }
    );
    httpServer.start();
  }

  String url()
  {
    return "http://localhost:" + httpServer.getAddress().getPort() + "/other";
  }

  Headers lastCall()
  {
    return calls.getLast();
  }

  void clear()
  {
    calls.clear();
  }

  @Override
  public void close()
  {
    httpServer.stop(0);
  }
}
