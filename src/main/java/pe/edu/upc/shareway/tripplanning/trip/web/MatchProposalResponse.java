package pe.edu.upc.shareway.tripplanning.trip.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposalStatus;

public record MatchProposalResponse(
    UUID id,
    @Schema(description = "Id of the driver availability (trip group) the proposal came from; "
        + "null for proposals generated before groups were tracked")
    UUID groupId,
    UUID driverId,
    @Schema(description = "Date of the trip group")
    LocalDate availableDate,
    @Schema(description = "Start of the time window of the trip group")
    LocalTime startTime,
    @Schema(description = "End of the time window of the trip group")
    LocalTime endTime,
    BigDecimal estimatedFare,
    int estimatedDurationMinutes,
    MatchProposalStatus status,
    Instant createdAt
) {
  public static MatchProposalResponse from(MatchProposal proposal) {
    DriverAvailability group = proposal.getDriverAvailability();
    return new MatchProposalResponse(
        proposal.getId(),
        group == null ? null : group.getId(),
        proposal.getDriverId(),
        group == null ? null : group.getAvailableDate(),
        group == null ? null : group.getStartTime(),
        group == null ? null : group.getEndTime(),
        proposal.getEstimatedFare(),
        proposal.getEstimatedDurationMinutes(),
        proposal.getStatus(),
        proposal.getCreatedAt()
    );
  }
}
