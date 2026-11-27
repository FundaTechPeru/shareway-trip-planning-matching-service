package pe.edu.upc.shareway.tripplanning.shared.outbox;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Stores integration events in the outbox table. */
@Service
public class OutboxService {

  private final OutboxEventRepository repository;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public OutboxService(OutboxEventRepository repository, JsonMapper jsonMapper, Clock clock) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  /**
   * Queues an event as {@code {"eventId", "occurredAt", "type", ...fields}}. It must run inside the
   * transaction of the change that produced the event, so both are committed or rolled back together.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent enqueue(String type, Map<String, ?> fields) {
    UUID eventId = UUID.randomUUID();
    // Database timestamps keep microseconds; the payload and the column carry the same instant.
    Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", eventId.toString());
    event.put("occurredAt", occurredAt.toString());
    event.put("type", type);
    fields.forEach((name, value) ->
        event.put(name, value instanceof UUID uuid ? uuid.toString() : value));

    return repository.save(
        new OutboxEvent(eventId, type, jsonMapper.writeValueAsString(event), occurredAt));
  }
}
