package pe.edu.upc.shareway.tripplanning.shared.security;

import java.util.Locale;
import java.util.Optional;

public enum UserRole {
  PASSENGER,
  DRIVER,
  ADMIN;

  static Optional<UserRole> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
