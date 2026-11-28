package pe.edu.upc.shareway.tripplanning.shared.integration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Booking &amp; Pricing service. The timeout is long because free Render services can take up to a
 * minute to wake up.
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
    @DefaultValue(BookingProperties.DEFAULT_URL) String url,
    @DefaultValue("90s") Duration timeout
) {

  public static final String DEFAULT_URL = "https://shareway-booking-pricing.onrender.com";

  public BookingProperties {
    // BOOKING_URL declared but left empty (for example in Render) falls back to the default.
    if (url == null || url.isBlank()) {
      url = DEFAULT_URL;
    }
  }
}
