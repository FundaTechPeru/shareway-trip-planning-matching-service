package pe.edu.upc.shareway.tripplanning.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP server standing in for Booking &amp; Pricing. Each request takes the next scripted
 * answer (200 when none is left) and is recorded.
 */
public final class StubBookingServer {

  public record ReceivedRequest(String method, String path, Map<String, List<String>> headers,
      String body) {

    public String header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .map(entry -> entry.getValue().getFirst())
          .findFirst()
          .orElse(null);
    }
  }

  private record Answer(int status, long delayMillis) {
  }

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final Queue<Answer> answers = new ConcurrentLinkedQueue<>();
  private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();

  public StubBookingServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
    server.createContext("/", this::handle);
    server.setExecutor(executor);
    server.start();
  }

  public String url() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  /** Scripts the status of the next requests, in order. */
  public void respond(int... statuses) {
    for (int status : statuses) {
      answers.add(new Answer(status, 0));
    }
  }

  /** Scripts the next request to answer only after {@code delayMillis}. */
  public void respondAfter(long delayMillis, int status) {
    answers.add(new Answer(status, delayMillis));
  }

  public List<ReceivedRequest> requests() {
    return List.copyOf(requests);
  }

  public void reset() {
    answers.clear();
    requests.clear();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(new ReceivedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
        Map.copyOf(exchange.getRequestHeaders()), body));

    Answer answer = answers.poll();
    if (answer == null) {
      answer = new Answer(200, 0);
    }
    if (answer.delayMillis() > 0) {
      try {
        Thread.sleep(answer.delayMillis());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] response = ("{\"status\":" + answer.status() + "}").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    try {
      exchange.sendResponseHeaders(answer.status(), response.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(response);
      }
    } catch (IOException exception) {
      // The client gave up (timeout test); nothing left to answer.
    } finally {
      exchange.close();
    }
  }
}
