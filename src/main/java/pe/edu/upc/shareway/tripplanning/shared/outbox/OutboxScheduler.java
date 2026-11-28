package pe.edu.upc.shareway.tripplanning.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "relay-enabled", havingValue = "true")
public class OutboxScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

  private final OutboxRelay relay;

  public OutboxScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval:5s}",
      initialDelayString = "${app.outbox.poll-interval:5s}")
  void relay() {
    try {
      relay.relayPending();
    } catch (RuntimeException exception) {
      // For example, the database is unreachable; the next run tries again.
      log.error("Outbox relay run failed", exception);
    }
  }
}
