package pe.edu.upc.shareway.tripplanning.shared.integration;

/** Result of sending one event to another service. */
public enum DeliveryOutcome {
  /** 2xx: the event was accepted. */
  DELIVERED,
  /** Timeout, connection error, 5xx, 401, 408 or 429: the same event must be sent again later. */
  RETRY,
  /** Any other 4xx: sending it again would fail the same way. */
  REJECTED;

  public static DeliveryOutcome ofStatus(int status) {
    if (status >= 200 && status < 300) {
      return DELIVERED;
    }
    if (status >= 400 && status < 500 && status != 401 && status != 408 && status != 429) {
      return REJECTED;
    }
    // 5xx, 401 (key not rolled out yet), 408, 429 and unexpected 1xx/3xx answers.
    return RETRY;
  }
}
