package pe.edu.upc.shareway.tripplanning.shared.security;

public class UnauthenticatedException extends RuntimeException {

  public UnauthenticatedException(String message) {
    super(message);
  }
}
