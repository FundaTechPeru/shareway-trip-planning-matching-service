package pe.edu.upc.shareway.tripplanning.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxSchedulerTests {

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(OutboxScheduler.class)
      .withBean(OutboxRelay.class, () -> mock(OutboxRelay.class));

  @Test
  void doesNotScheduleDeliveryWithoutAnExplicitOptIn() {
    context.run(application -> assertThat(application).doesNotHaveBean(OutboxScheduler.class));
  }

  @Test
  void schedulesDeliveryWhenExplicitlyEnabled() {
    context.withPropertyValues("app.outbox.relay-enabled=true")
        .run(application -> assertThat(application).hasSingleBean(OutboxScheduler.class));
  }
}
