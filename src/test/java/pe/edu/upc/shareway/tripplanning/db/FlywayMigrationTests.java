package pe.edu.upc.shareway.tripplanning.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Runs the real migrations on dedicated H2 databases to check that they keep existing rows. */
class FlywayMigrationTests {

	private static final String URL = "jdbc:h2:mem:flyway_migration_test;MODE=PostgreSQL;"
			+ "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
	private static final String V4_URL = "jdbc:h2:mem:flyway_v4_migration_test;MODE=PostgreSQL;"
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

	@Test
	void v4AddsGroupStatusProposalGroupAndOutboxPreservingRows() throws SQLException {
		// H2 (not PostgreSQL) evaluates a CHECK constraint with the session that created it, which
		// fails once that session is closed. The migration connection is therefore kept open for the
		// whole test, as the connection pool of the Spring tests does.
		SingleConnectionDataSource dataSource = new SingleConnectionDataSource(V4_URL, "sa", "", true);
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			flyway(dataSource, "3").migrate();
			insertTripAvailabilityAndProposal(statement, "'" + UUID.randomUUID() + "'", "'" + UUID.randomUUID() + "'");

			flyway(dataSource, null).migrate();

			try (ResultSet resultSet = statement.executeQuery(
					"select status from driver_availabilities where id = '" + AVAILABILITY_ID + "'")) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString(1)).isEqualTo("OPEN");
			}
			try (ResultSet resultSet = statement.executeQuery(
					"select driver_availability_id from match_proposals where id = '" + PROPOSAL_ID + "'")) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getObject(1)).isNull();
			}
			assertColumn(statement, "driver_availabilities", "status", "character varying", false);
			assertColumn(statement, "match_proposals", "driver_availability_id", "uuid", true);

			try (ResultSet event = statement.executeQuery(
					"select id, type, payload from outbox order by sequence_number")) {
				assertThat(event.next()).isTrue();
				assertThat(event.getString("type")).isEqualTo("TripGroupOpened");
				String payload = event.getString("payload");
				assertThat((String) JsonPath.read(payload, "$.eventId"))
						.isEqualTo(event.getObject("id", UUID.class).toString());
				assertThat((String) JsonPath.read(payload, "$.groupId"))
						.isEqualTo(AVAILABILITY_ID.toString());
				assertThat((Integer) JsonPath.read(payload, "$.capacity")).isEqualTo(3);
				assertThat(Instant.parse(JsonPath.<String>read(payload, "$.occurredAt"))).isNotNull();
				assertThat(event.next()).isFalse();
			}

			// Only OPEN, ACCEPTED and REJECTED are valid statuses.
			statement.executeUpdate(
					"update driver_availabilities set status = 'ACCEPTED' where id = '" + AVAILABILITY_ID + "'");
			assertThatThrownBy(() -> statement.executeUpdate(
					"update driver_availabilities set status = 'CLOSED' where id = '" + AVAILABILITY_ID + "'"))
					.isInstanceOf(SQLException.class);

			// The proposal can now point to its group.
			statement.executeUpdate("update match_proposals set driver_availability_id = '"
					+ AVAILABILITY_ID + "' where id = '" + PROPOSAL_ID + "'");

			// The outbox assigns increasing sequence numbers and defaults attempts to 0.
			for (String type : new String[] {"GroupCompleted", "ProposalAccepted"}) {
				statement.executeUpdate("""
						insert into outbox (id, type, payload, occurred_at, next_attempt_at)
						values ('%s', '%s', '{}', current_timestamp, current_timestamp)
						""".formatted(UUID.randomUUID(), type));
			}
			try (ResultSet resultSet = statement.executeQuery(
					"select type, attempts, processed_at, failed_at, last_error from outbox order by sequence_number")) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("type")).isEqualTo("TripGroupOpened");
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("type")).isEqualTo("GroupCompleted");
				assertThat(resultSet.getInt("attempts")).isZero();
				assertThat(resultSet.getObject("processed_at")).isNull();
				assertThat(resultSet.getObject("failed_at")).isNull();
				assertThat(resultSet.getObject("last_error")).isNull();
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("type")).isEqualTo("ProposalAccepted");
			}
			for (String column : new String[] {"id", "sequence_number", "type", "payload", "occurred_at",
					"processed_at", "failed_at", "attempts", "next_attempt_at", "last_error"}) {
				try (ResultSet resultSet = statement.executeQuery("""
						select 1 from information_schema.columns
						where table_name = 'outbox' and column_name = '%s'
						""".formatted(column))) {
					assertThat(resultSet.next()).as("outbox." + column).isTrue();
				}
			}
		} finally {
			dataSource.destroy();
		}
	}

	private static void insertTripAvailabilityAndProposal(Statement statement, String passengerId,
			String driverId) throws SQLException {
		statement.executeUpdate("""
				insert into trip_requests (id, passenger_id, origin_label, destination_label,
				    operation_zone, origin_lat, origin_lng, destination_lat, destination_lng,
				    travel_date, earliest_departure, latest_departure, seats_requested, status,
				    created_at)
				values ('%s', %s, 'UPC Monterrico', 'Chorrillos', 'Chorrillos', -12.1048,
				    -76.9632, -12.1721, -77.0164, date '2026-10-04', time '07:30:00',
				    time '08:30:00', 1, 'PENDING_MATCH', current_timestamp)
				""".formatted(TRIP_ID, passengerId));
		statement.executeUpdate("""
				insert into driver_availabilities (id, driver_id, operation_zone, available_date,
				    start_time, end_time, available_seats)
				values ('%s', %s, 'Chorrillos', date '2026-10-04', time '07:00:00',
				    time '09:00:00', 3)
				""".formatted(AVAILABILITY_ID, driverId));
		statement.executeUpdate("""
				insert into match_proposals (id, trip_request_id, driver_id, estimated_fare,
				    estimated_duration_minutes, status, created_at)
				values ('%s', '%s', %s, 7.70, 60, 'PROPOSED', current_timestamp)
				""".formatted(PROPOSAL_ID, TRIP_ID, driverId));
	}

	private static void assertColumn(Statement statement, String table, String column, String dataType,
			boolean nullable) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
				select data_type, is_nullable from information_schema.columns
				where table_name = '%s' and column_name = '%s'
				""".formatted(table, column))) {
			assertThat(resultSet.next()).as(table + "." + column).isTrue();
			assertThat(resultSet.getString("data_type")).isEqualToIgnoringCase(dataType);
			assertThat(resultSet.getString("is_nullable")).isEqualToIgnoringCase(nullable ? "YES" : "NO");
		}
	}

	private static Flyway flyway(String target) {
		return flyway(URL, target);
	}

	private static Flyway flyway(String url, String target) {
		return flyway(Flyway.configure().dataSource(url, "sa", ""), target);
	}

	private static Flyway flyway(DataSource dataSource, String target) {
		return flyway(Flyway.configure().dataSource(dataSource), target);
	}

	private static Flyway flyway(FluentConfiguration base, String target) {
		var configuration = base.locations("classpath:db/migration");
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
