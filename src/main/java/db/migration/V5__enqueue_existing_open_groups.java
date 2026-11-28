package db.migration;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.json.JsonMapper;

/** Registers availabilities created before Trip Planning started publishing group events. */
public class V5__enqueue_existing_open_groups extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    var connection = context.getConnection();
    var jsonMapper = new JsonMapper();
    var occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    String eventType = "TripGroupOpened";

    try (var select = connection.prepareStatement("""
            select id, available_seats from driver_availabilities where status = 'OPEN' order by id
            """);
        var groups = select.executeQuery();
        var insert = connection.prepareStatement("""
            insert into outbox (id, type, payload, occurred_at, next_attempt_at)
            values (?, ?, ?, ?, ?)
            """)) {
      while (groups.next()) {
        UUID eventId = UUID.randomUUID();
        UUID groupId = groups.getObject("id", UUID.class);
        String payload = jsonMapper.writeValueAsString(Map.of(
            "eventId", eventId.toString(),
            "occurredAt", occurredAt.toInstant().toString(),
            "type", eventType,
            "groupId", groupId.toString(),
            "capacity", groups.getInt("available_seats")
        ));
        insert.setObject(1, eventId);
        insert.setString(2, eventType);
        insert.setString(3, payload);
        insert.setObject(4, occurredAt);
        insert.setObject(5, occurredAt);
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }
}
