package pe.edu.upc.shareway.tripplanning.shared.integration;

/** Result of sending one event to another service. */
public enum DeliveryOutcome {
  /** 2xx: the event was accepted. */
  DELIVERED,
  /** A transient transport error or an unavailable receiver: send the same event again later. */
  RETRY,
  /** A permanent 4xx: sending it again would fail the same way. */
  REJECTED;

  public static DeliveryOutcome ofStatus(int status) {
    if (status >= 200 && status < 300) {
      return DELIVERED;
    }
    return switch (status) {
      // The receiver can be missing, use an earlier route or still need the shared key.
      case 401, 404, 405, 408, 429 -> RETRY;
      default -> status >= 400 && status < 500 ? REJECTED : RETRY;
    };
  }
}
