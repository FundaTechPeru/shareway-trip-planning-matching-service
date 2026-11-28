package pe.edu.upc.shareway.tripplanning.trip.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposalStatus;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, UUID> {

  /** Also loads the trip group, whose date and time window are part of the response. */
  @EntityGraph(attributePaths = "driverAvailability")
  List<MatchProposal> findByTripRequestIdOrderByCreatedAtDesc(UUID tripRequestId);

  List<MatchProposal> findByDriverAvailabilityId(UUID driverAvailabilityId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update MatchProposal proposal
      set proposal.status = :to
      where proposal.driverAvailability.id = :driverAvailabilityId
        and proposal.status = :from
      """)
  int transitionStatusByDriverAvailability(
      @Param("driverAvailabilityId") UUID driverAvailabilityId,
      @Param("from") MatchProposalStatus from,
      @Param("to") MatchProposalStatus to
  );
}
