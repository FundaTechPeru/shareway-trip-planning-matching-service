package pe.edu.upc.shareway.tripplanning.trip.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> {

  List<MatchProposal> findByTripRequestIdOrderByCreatedAtDesc(Long tripRequestId);
}
