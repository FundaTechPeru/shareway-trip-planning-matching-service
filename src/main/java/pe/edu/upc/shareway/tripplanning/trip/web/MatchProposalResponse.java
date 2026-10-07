package pe.edu.upc.shareway.tripplanning.trip.web;

import java.math.BigDecimal;
import java.time.Instant;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposalStatus;

public record MatchProposalResponse(
    Long id,
    Long driverId,
    BigDecimal estimatedFare,
    int estimatedDurationMinutes,
    MatchProposalStatus status,
    Instant createdAt
) {
  public static MatchProposalResponse from(MatchProposal proposal) {
    return new MatchProposalResponse(
        proposal.getId(),
        proposal.getDriverId(),
        proposal.getEstimatedFare(),
        proposal.getEstimatedDurationMinutes(),
        proposal.getStatus(),
        proposal.getCreatedAt()
    );
  }
}
