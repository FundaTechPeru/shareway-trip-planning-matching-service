package pe.edu.upc.shareway.tripplanning.shared.integration;

/**
 * @param outcome what to do with the event
 * @param detail  HTTP status and response excerpt, or the I/O error; {@code null} when delivered
 */
public record DeliveryResult(DeliveryOutcome outcome, String detail) {
}
