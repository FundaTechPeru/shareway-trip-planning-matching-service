package pe.edu.upc.shareway.tripplanning.shared.integration;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Sends integration events to {@code POST {BOOKING_URL}/api/v1/internal/events}. */
@Component
public class BookingEventsClient {

  static final String EVENTS_PATH = "/api/v1/internal/events";
  private static final int MAX_DETAIL_LENGTH = 500;

  private final RestClient restClient;

  public BookingEventsClient(BookingProperties booking, InternalApiProperties internalApi) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(booking.timeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(booking.timeout());

    this.restClient = RestClient.builder()
        .baseUrl(stripTrailingSlash(booking.url()))
        .requestFactory(requestFactory)
        .defaultHeader(InternalApiProperties.API_KEY_HEADER, internalApi.apiKey())
        .build();
  }

  /** Posts the event JSON as is. Never throws: errors are reported in the result. */
  public DeliveryResult send(String eventJson) {
    try {
      return restClient.post()
          .uri(EVENTS_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(eventJson)
          .exchange((request, response) -> {
            int status = response.getStatusCode().value();
            DeliveryOutcome outcome = DeliveryOutcome.ofStatus(status);
            if (outcome == DeliveryOutcome.DELIVERED) {
              return new DeliveryResult(outcome, null);
            }
            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            return new DeliveryResult(outcome, truncate("HTTP " + status + ": " + body));
          });
    } catch (ResourceAccessException exception) {
      // Timeouts, refused connections, DNS and other I/O errors.
      return new DeliveryResult(DeliveryOutcome.RETRY, truncate("I/O error: " + exception.getMessage()));
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String truncate(String detail) {
    return detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH);
  }
}
