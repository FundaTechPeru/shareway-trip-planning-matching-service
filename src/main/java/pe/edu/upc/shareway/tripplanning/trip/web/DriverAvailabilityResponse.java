package pe.edu.upc.shareway.tripplanning.trip.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailabilityStatus;

public record DriverAvailabilityResponse(
    @Schema(description = "Availability id; Booking & Pricing uses it as the groupId")
    UUID id,
    UUID driverId,
    String operationZone,
    LocalDate availableDate,
    LocalTime startTime,
    LocalTime endTime,
    int availableSeats,
    DriverAvailabilityStatus status
) {
  public static DriverAvailabilityResponse from(DriverAvailability availability) {
    return new DriverAvailabilityResponse(
        availability.getId(),
        availability.getDriverId(),
        availability.getOperationZone(),
        availability.getAvailableDate(),
        availability.getStartTime(),
        availability.getEndTime(),
        availability.getAvailableSeats(),
        availability.getStatus()
    );
  }
}
