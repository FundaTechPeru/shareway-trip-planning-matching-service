package pe.edu.upc.shareway.tripplanning.trip.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateDriverAvailabilityRequest(
    @NotNull Long driverId,
    @NotBlank String operationZone,
    @NotNull LocalDate availableDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Min(1) int availableSeats
) {
}
