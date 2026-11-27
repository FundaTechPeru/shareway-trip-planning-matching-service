package pe.edu.upc.shareway.tripplanning.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Integration event waiting to be sent to Booking &amp; Pricing. It is stored in the same transaction
 * as the change that produced it and relayed later by {@link OutboxRelay}, in sequence order.
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

  /** Same value as the {@code eventId} inside the payload, so retries are idempotent downstream. */
  @Id
  private UUID id;

  /** Assigned by the database on insert; defines the delivery order. */
  @Column(name = "sequence_number", insertable = false, updatable = false)
  private Long sequenceNumber;

  @Column(nullable = false, length = 100)
  private String type;

  @Column(nullable = false, length = 4000)
  private String payload;

  @Column(nullable = false)
  private Instant occurredAt;

  private Instant processedAt;

  private Instant failedAt;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private Instant nextAttemptAt;

  @Column(length = 1000)
  private String lastError;

  protected OutboxEvent() {
  }

  OutboxEvent(UUID id, String type, String payload, Instant occurredAt) {
    this.id = id;
    this.type = type;
    this.payload = payload;
    this.occurredAt = occurredAt;
    this.nextAttemptAt = occurredAt;
  }

  void markProcessed(Instant now) {
    attempts++;
    processedAt = now;
    lastError = null;
  }

  void scheduleRetry(Instant now, Duration delay, String error) {
    attempts++;
    nextAttemptAt = now.plus(delay);
    lastError = error;
  }

  void markFailed(Instant now, String error) {
    attempts++;
    failedAt = now;
    lastError = error;
  }

  public UUID getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public Instant getFailedAt() {
    return failedAt;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }
}
