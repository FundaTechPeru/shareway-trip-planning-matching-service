package pe.edu.upc.shareway.tripplanning.trip.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for {@code POST /api/v1/driver-availabilities}. The driver is the subject of the bearer
 * token; a {@code driverId} sent by older clients is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateDriverAvailabilityRequest(
    @NotBlank String operationZone,
    @NotNull LocalDate availableDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Min(1) int availableSeats
) {
}
