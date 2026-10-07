package pe.edu.upc.shareway.tripplanning.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "driver_availabilities")
public class DriverAvailability {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Column(nullable = false)
  private Long driverId;

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

  protected DriverAvailability() {
  }

  public DriverAvailability(Long driverId, String operationZone, LocalDate availableDate,
      LocalTime startTime, LocalTime endTime, int availableSeats) {
    this.driverId = driverId;
    this.operationZone = operationZone;
    this.availableDate = availableDate;
    this.startTime = startTime;
    this.endTime = endTime;
    this.availableSeats = availableSeats;
  }

  public Long getId() {
    return id;
  }

  public Long getDriverId() {
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
}
