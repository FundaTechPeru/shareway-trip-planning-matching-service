package pe.edu.upc.shareway.tripplanning.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Runs the real migrations on a dedicated H2 database to check that V3 keeps existing rows. */
class FlywayMigrationTests {

	private static final String URL = "jdbc:h2:mem:flyway_migration_test;MODE=PostgreSQL;"
			+ "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

	private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID AVAILABILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID PROPOSAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

	@Test
	void v3ConvertsUserIdsToUuidPreservingRows() throws SQLException {
		flyway("2").migrate();

		try (Connection connection = DriverManager.getConnection(URL, "sa", "");
				Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					insert into trip_requests (id, passenger_id, origin_label, destination_label,
					    operation_zone, origin_lat, origin_lng, destination_lat, destination_lng,
					    travel_date, earliest_departure, latest_departure, seats_requested, status,
					    created_at)
					values ('%s', 25, 'UPC Monterrico', 'Chorrillos', 'Chorrillos', -12.1048,
					    -76.9632, -12.1721, -77.0164, date '2026-10-04', time '07:30:00',
					    time '08:30:00', 1, 'PENDING_MATCH', current_timestamp)
					""".formatted(TRIP_ID));
			statement.executeUpdate("""
					insert into driver_availabilities (id, driver_id, operation_zone, available_date,
					    start_time, end_time, available_seats)
					values ('%s', 10, 'Chorrillos', date '2026-10-04', time '07:00:00',
					    time '09:00:00', 3)
					""".formatted(AVAILABILITY_ID));
			statement.executeUpdate("""
					insert into match_proposals (id, trip_request_id, driver_id, estimated_fare,
					    estimated_duration_minutes, status, created_at)
					values ('%s', '%s', 9223372036854775807, 7.70, 60, 'PROPOSED', current_timestamp)
					""".formatted(PROPOSAL_ID, TRIP_ID));
		}

		flyway(null).migrate();

		try (Connection connection = DriverManager.getConnection(URL, "sa", "");
				Statement statement = connection.createStatement()) {
			assertThat(singleUuid(statement,
					"select passenger_id from trip_requests where id = '" + TRIP_ID + "'"))
					.isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000025"));
			assertThat(singleUuid(statement,
					"select driver_id from driver_availabilities where id = '" + AVAILABILITY_ID + "'"))
					.isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000010"));
			// Largest BIGINT: 19 digits still fit in the 32 hex characters of a UUID.
			assertThat(singleUuid(statement,
					"select driver_id from match_proposals where id = '" + PROPOSAL_ID + "'"))
					.isEqualTo(UUID.fromString("00000000-0000-0922-3372-036854775807"));
			assertThat(singleUuid(statement,
					"select trip_request_id from match_proposals where id = '" + PROPOSAL_ID + "'"))
					.isEqualTo(TRIP_ID);

			String[][] columns = {
					{"trip_requests", "passenger_id"},
					{"driver_availabilities", "driver_id"},
					{"match_proposals", "driver_id"}
			};
			for (String[] column : columns) {
				try (ResultSet resultSet = statement.executeQuery("""
						select data_type, is_nullable from information_schema.columns
						where table_name = '%s' and column_name = '%s'
						""".formatted(column[0], column[1]))) {
					assertThat(resultSet.next()).as(column[0] + "." + column[1]).isTrue();
					assertThat(resultSet.getString("data_type")).isEqualToIgnoringCase("uuid");
					assertThat(resultSet.getString("is_nullable")).isEqualToIgnoringCase("NO");
				}
			}
		}
	}

	private static Flyway flyway(String target) {
		var configuration = Flyway.configure()
				.dataSource(URL, "sa", "")
				.locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private static UUID singleUuid(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).as(sql).isTrue();
			return resultSet.getObject(1, UUID.class);
		}
	}
}
