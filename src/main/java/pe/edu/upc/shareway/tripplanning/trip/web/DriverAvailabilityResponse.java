package pe.edu.upc.shareway.tripplanning.trip.web;

import java.time.LocalDate;
import java.time.LocalTime;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;

public record DriverAvailabilityResponse(
    Long id,
    Long driverId,
    String operationZone,
    LocalDate availableDate,
    LocalTime startTime,
    LocalTime endTime,
    int availableSeats
) {
  public static DriverAvailabilityResponse from(DriverAvailability availability) {
    return new DriverAvailabilityResponse(
        availability.getId(),
        availability.getDriverId(),
        availability.getOperationZone(),
        availability.getAvailableDate(),
        availability.getStartTime(),
        availability.getEndTime(),
        availability.getAvailableSeats()
    );
  }
}
