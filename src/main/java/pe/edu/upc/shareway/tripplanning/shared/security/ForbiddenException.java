package pe.edu.upc.shareway.tripplanning.shared.security;

public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
