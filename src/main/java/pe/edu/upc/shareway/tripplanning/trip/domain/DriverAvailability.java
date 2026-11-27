package pe.edu.upc.shareway.tripplanning.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Availability published by a driver. Booking &amp; Pricing calls it a trip group: its id is the
 * {@code groupId} of the integration events.
 */
@Entity
@Table(name = "driver_availabilities")
public class DriverAvailability {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(nullable = false)
  private UUID driverId;

  @NotBlank
  @Column(nullable = false, length = 120)
  private String operationZone;

  @NotNull
  @Column(nullable = false)
  private LocalDate availableDate;

  @NotNull
  @Column(nullable = false)
  private LocalTime startTime;

  @NotNull
  @Column(nullable = false)
  private LocalTime endTime;

  @Min(1)
  @Column(nullable = false)
  private int availableSeats;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DriverAvailabilityStatus status = DriverAvailabilityStatus.OPEN;

  protected DriverAvailability() {
  }

  public DriverAvailability(UUID driverId, String operationZone, LocalDate availableDate,
      LocalTime startTime, LocalTime endTime, int availableSeats) {
    this.driverId = driverId;
    this.operationZone = operationZone;
    this.availableDate = availableDate;
    this.startTime = startTime;
    this.endTime = endTime;
    this.availableSeats = availableSeats;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDriverId() {
    return driverId;
  }

  public String getOperationZone() {
    return operationZone;
  }

  public LocalDate getAvailableDate() {
    return availableDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public int getAvailableSeats() {
    return availableSeats;
  }

  public DriverAvailabilityStatus getStatus() {
    return status;
  }
}
