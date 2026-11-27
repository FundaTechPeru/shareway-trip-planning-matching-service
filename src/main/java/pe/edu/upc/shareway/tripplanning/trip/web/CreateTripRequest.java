package pe.edu.upc.shareway.tripplanning.trip.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for {@code POST /api/v1/trip-requests}. The passenger is taken from the {@code X-User-Id}
 * gateway header; a {@code passengerId} sent by older clients is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTripRequest(
    @NotBlank String originLabel,
    @NotBlank String destinationLabel,
    @NotBlank String operationZone,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal originLat,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal originLng,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal destinationLat,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal destinationLng,
    @NotNull LocalDate travelDate,
    @NotNull LocalTime earliestDeparture,
    @NotNull LocalTime latestDeparture,
    @Min(1) int seatsRequested
) {
}
