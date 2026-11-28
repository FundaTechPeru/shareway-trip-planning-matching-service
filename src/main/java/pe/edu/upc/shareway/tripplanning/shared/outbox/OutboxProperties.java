package pe.edu.upc.shareway.tripplanning.shared.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param relayEnabled   whether the scheduled relay runs (tests turn it off and call it directly)
 * @param pollInterval   delay between two relay runs
 * @param batchSize      maximum events sent per run
 * @param initialBackoff wait after the first retryable error; it doubles on each new error
 * @param maxBackoff     upper bound of the wait between two attempts
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
    @DefaultValue("false") boolean relayEnabled,
    @DefaultValue("5s") Duration pollInterval,
    @DefaultValue("50") int batchSize,
    @DefaultValue("5s") Duration initialBackoff,
    @DefaultValue("5m") Duration maxBackoff
) {
}
