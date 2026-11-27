-- ShareWay users are identified by UUID (the JWT subject issued by Driver Management), so the
-- passenger_id and driver_id references change from BIGINT to UUID. Existing rows are preserved:
-- each old numeric value is encoded as a deterministic UUID with the same pattern used in V2
-- (for example 25 becomes 00000000-0000-0000-0000-000000000025). The same SQL runs on PostgreSQL
-- and on H2 in PostgreSQL mode.
alter table trip_requests add column passenger_id_uuid uuid;
alter table driver_availabilities add column driver_id_uuid uuid;
alter table match_proposals add column driver_id_uuid uuid;

update trip_requests
set passenger_id_uuid = cast(
    substring(lpad(cast(passenger_id as varchar), 32, '0'), 1, 8) || '-' ||
    substring(lpad(cast(passenger_id as varchar), 32, '0'), 9, 4) || '-' ||
    substring(lpad(cast(passenger_id as varchar), 32, '0'), 13, 4) || '-' ||
    substring(lpad(cast(passenger_id as varchar), 32, '0'), 17, 4) || '-' ||
    substring(lpad(cast(passenger_id as varchar), 32, '0'), 21, 12) as uuid
);

update driver_availabilities
set driver_id_uuid = cast(
    substring(lpad(cast(driver_id as varchar), 32, '0'), 1, 8) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 9, 4) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 13, 4) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 17, 4) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 21, 12) as uuid
);

update match_proposals
set driver_id_uuid = cast(
    substring(lpad(cast(driver_id as varchar), 32, '0'), 1, 8) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 9, 4) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 13, 4) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 17, 4) || '-' ||
    substring(lpad(cast(driver_id as varchar), 32, '0'), 21, 12) as uuid
);

alter table trip_requests alter column passenger_id_uuid set not null;
alter table driver_availabilities alter column driver_id_uuid set not null;
alter table match_proposals alter column driver_id_uuid set not null;

-- No existing index includes these columns, so they can be dropped directly.
alter table trip_requests drop column passenger_id;
alter table driver_availabilities drop column driver_id;
alter table match_proposals drop column driver_id;

alter table trip_requests rename column passenger_id_uuid to passenger_id;
alter table driver_availabilities rename column driver_id_uuid to driver_id;
alter table match_proposals rename column driver_id_uuid to driver_id;

-- GET /api/v1/trip-requests now filters by the requesting passenger.
create index idx_trip_requests_passenger
    on trip_requests (passenger_id, created_at desc);
