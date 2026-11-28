package pe.edu.upc.shareway.tripplanning.shared.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /** Events neither processed nor failed, oldest first. */
  @Query("""
      select event
      from OutboxEvent event
      where event.processedAt is null
        and event.failedAt is null
      order by event.sequenceNumber asc
      """)
  List<OutboxEvent> findPending(Pageable pageable);

  @Query("select event from OutboxEvent event order by event.sequenceNumber asc")
  List<OutboxEvent> findAllInOrder();
}
