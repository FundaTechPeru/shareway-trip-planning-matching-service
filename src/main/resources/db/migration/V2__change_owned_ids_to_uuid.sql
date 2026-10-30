-- Preserve existing rows and references while changing IDs owned by this service to UUID.
-- External passenger_id and driver_id values stay BIGINT to match Identity/Driver Management.
alter table trip_requests add column id_uuid uuid;
alter table driver_availabilities add column id_uuid uuid;
alter table match_proposals add column id_uuid uuid;
alter table match_proposals add column trip_request_id_uuid uuid;

-- Existing identity values are encoded as valid, deterministic UUIDs. New rows use UUID generation
-- in the application. This keeps IDs stable through migration and permits the same SQL on H2/PG.
update trip_requests
set id_uuid = cast(
    substring(lpad(cast(id as varchar), 32, '0'), 1, 8) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 9, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 13, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 17, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 21, 12) as uuid
);

update driver_availabilities
set id_uuid = cast(
    substring(lpad(cast(id as varchar), 32, '0'), 1, 8) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 9, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 13, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 17, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 21, 12) as uuid
);

update match_proposals
set id_uuid = cast(
    substring(lpad(cast(id as varchar), 32, '0'), 1, 8) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 9, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 13, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 17, 4) || '-' ||
    substring(lpad(cast(id as varchar), 32, '0'), 21, 12) as uuid
);

update match_proposals
set trip_request_id_uuid = (
    select trip_requests.id_uuid
    from trip_requests
    where trip_requests.id = match_proposals.trip_request_id
);

alter table trip_requests alter column id_uuid set not null;
alter table driver_availabilities alter column id_uuid set not null;
alter table match_proposals alter column id_uuid set not null;
alter table match_proposals alter column trip_request_id_uuid set not null;

-- Remove the old index and foreign-key column before replacing their referenced keys.
drop index idx_match_proposals_trip_request;
alter table match_proposals drop column trip_request_id;
alter table trip_requests drop column id;
alter table driver_availabilities drop column id;
alter table match_proposals drop column id;

alter table trip_requests rename column id_uuid to id;
alter table driver_availabilities rename column id_uuid to id;
alter table match_proposals rename column id_uuid to id;
alter table match_proposals rename column trip_request_id_uuid to trip_request_id;

alter table trip_requests add constraint pk_trip_requests primary key (id);
alter table driver_availabilities add constraint pk_driver_availabilities primary key (id);
alter table match_proposals add constraint pk_match_proposals primary key (id);
alter table match_proposals
    add constraint fk_match_proposals_trip_request
    foreign key (trip_request_id) references trip_requests (id);

create index idx_match_proposals_trip_request
    on match_proposals (trip_request_id, created_at desc);
