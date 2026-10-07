package pe.edu.upc.shareway.tripplanning.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "match_proposals")
public class MatchProposal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "trip_request_id", nullable = false)
  private TripRequest tripRequest;

  @Column(nullable = false)
  private Long driverId;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal estimatedFare;

  @Column(nullable = false)
  private int estimatedDurationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MatchProposalStatus status = MatchProposalStatus.PROPOSED;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  protected MatchProposal() {
  }

  public MatchProposal(TripRequest tripRequest, Long driverId, BigDecimal estimatedFare,
      int estimatedDurationMinutes) {
    this.tripRequest = tripRequest;
    this.driverId = driverId;
    this.estimatedFare = estimatedFare;
    this.estimatedDurationMinutes = estimatedDurationMinutes;
  }

  public Long getId() {
    return id;
  }

  public Long getDriverId() {
    return driverId;
  }

  public BigDecimal getEstimatedFare() {
    return estimatedFare;
  }

  public int getEstimatedDurationMinutes() {
    return estimatedDurationMinutes;
  }

  public MatchProposalStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
