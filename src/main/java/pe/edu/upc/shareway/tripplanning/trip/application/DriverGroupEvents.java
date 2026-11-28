package pe.edu.upc.shareway.tripplanning.trip.application;

/** Types of the integration events sent to Booking &amp; Pricing about trip groups. */
public final class DriverGroupEvents {

  /** A driver published an availability. Fields: groupId, capacity. */
  public static final String TRIP_GROUP_OPENED = "TripGroupOpened";
  /** The driver accepted the group; no more passengers join it. Fields: groupId. */
  public static final String GROUP_COMPLETED = "GroupCompleted";
  /** The driver accepted the proposals of the group. Fields: groupId, driverId. */
  public static final String PROPOSAL_ACCEPTED = "ProposalAccepted";
  /** The driver rejected the group. Fields: groupId, driverId. */
  public static final String GROUP_REJECTED = "GroupRejected";

  private DriverGroupEvents() {
  }
}
