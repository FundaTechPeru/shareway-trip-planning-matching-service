package pe.edu.upc.shareway.tripplanning.shared.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared key sent in the X-Internal-Api-Key header on calls between ShareWay services. */
@ConfigurationProperties(prefix = "app.internal")
public record InternalApiProperties(String apiKey) {

  public static final String API_KEY_HEADER = "X-Internal-Api-Key";
}
