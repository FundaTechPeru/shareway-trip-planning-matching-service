package pe.edu.upc.shareway.tripplanning.shared.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import pe.edu.upc.shareway.tripplanning.shared.integration.BookingEventsClient;
import pe.edu.upc.shareway.tripplanning.shared.integration.DeliveryResult;

/**
 * Sends pending outbox events to Booking &amp; Pricing in sequence order.
 *
 * <ul>
 *   <li>Delivered (2xx): the event is marked processed.</li>
 *   <li>Retryable error (timeout, connection error, 5xx, 401, 404, 405, 408, 429): the event waits with
 *       exponential backoff and the run stops, so later events never overtake it.</li>
 *   <li>Rejected (any other 4xx): the event is marked failed, the error is logged and the run goes
 *       on with the next event.</li>
 * </ul>
 *
 * <p>The HTTP call runs outside any database transaction, so a slow Booking wake-up (up to 90 s)
 * does not hold a connection. Delivery can overlap during a deployment. Booking deduplicates
 * stable event IDs, and optimistic locking keeps an older attempt from overwriting a newer one.
 */
@Component
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxEventRepository repository;
  private final BookingEventsClient bookingEventsClient;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxRelay(OutboxEventRepository repository, BookingEventsClient bookingEventsClient,
      OutboxProperties properties, Clock clock) {
    this.repository = repository;
    this.bookingEventsClient = bookingEventsClient;
    this.properties = properties;
    this.clock = clock;
  }

  public synchronized void relayPending() {
    List<OutboxEvent> pending = repository.findPending(PageRequest.of(0, properties.batchSize()));
    for (OutboxEvent event : pending) {
      if (event.getNextAttemptAt().isAfter(Instant.now(clock))) {
        // The oldest pending event is still backing off: wait to keep the order.
        return;
      }

      DeliveryResult result = bookingEventsClient.send(event.getPayload());
      Instant now = Instant.now(clock);
      switch (result.outcome()) {
        case DELIVERED -> {
          event.markProcessed(now);
          repository.save(event);
        }
        case RETRY -> {
          Duration delay = backoff(event.getAttempts() + 1);
          event.scheduleRetry(now, delay, result.detail());
          repository.save(event);
          log.warn("Outbox event {} ({}) will be retried in {} after attempt {}: {}",
              event.getId(), event.getType(), delay, event.getAttempts(), result.detail());
          return;
        }
        case REJECTED -> {
          event.markFailed(now, result.detail());
          repository.save(event);
          log.error("Outbox event {} ({}) was rejected by Booking and will not be retried: {}",
              event.getId(), event.getType(), result.detail());
        }
      }
    }
  }

  /** Wait after {@code failedAttempts} failures: initialBackoff * 2^(failedAttempts - 1), capped. */
  Duration backoff(int failedAttempts) {
    Duration delay = properties.initialBackoff();
    for (int i = 1; i < failedAttempts && delay.compareTo(properties.maxBackoff()) < 0; i++) {
      delay = delay.multipliedBy(2);
    }
    return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
  }
}
