-- One-shot migration from the old coreprotect-clickhouse fork schema to the
-- official PlayPro/CoreProtect ClickHouse event_data schema.
--
-- Assumptions:
--   old/source database: kostya
--   new/target database: coreprotect_playpro
--   table prefix in both databases: co_
--
-- Run this only after the official PlayPro/CoreProtect jar has started once
-- against coreprotect_playpro and created its empty ClickHouse schema.
-- Stop the Minecraft server before running this file.
--
-- Do not run this against the old database as the target. The official schema
-- creates compatibility views named co_block/co_container/etc, which conflict
-- with the fork's physical tables.

CREATE DATABASE IF NOT EXISTS coreprotect_playpro ENGINE = Atomic;

SELECT 'target identity rows' AS check_name, count()
FROM coreprotect_playpro.co_storage_metadata;

SELECT 'target event rows before migration' AS check_name, family, count()
FROM coreprotect_playpro.co_event_data
GROUP BY family
ORDER BY family;

-- art_map
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 10, toUUID('00000000-0000-0000-0000-000000000010'), 0, 'art_map',
    rowid, 0, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, id, art, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_art_map
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- block
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 20, toUUID('00000000-0000-0000-0000-000000000020'), 0, 'block',
    rowid, time, user, wid, x, y, z, type, data, NULL, nullIf(meta, ''), nullIf(toString(blockdata), ''),
    action, rolled_back, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_block FINAL
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- chat
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 30, toUUID('00000000-0000-0000-0000-000000000030'), 0, 'chat',
    rowid, time, user, wid, x, y, z, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    message, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_chat
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- command
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 40, toUUID('00000000-0000-0000-0000-000000000040'), 0, 'command',
    rowid, time, user, wid, x, y, z, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    message, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_command
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- container
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 50, toUUID('00000000-0000-0000-0000-000000000050'), 0, 'container',
    rowid, time, user, wid, x, y, z, type, toInt64(data), NULL, NULL, NULL,
    action, rolled_back, toInt32(amount), nullIf(metadata, ''), 0, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_container FINAL
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- entity_container
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 60, toUUID('00000000-0000-0000-0000-000000000060'), 0, 'entity_container',
    rowid, time, user, wid, x, y, z, type, toInt64(data), NULL, NULL, NULL,
    action, rolled_back, toInt32(amount), nullIf(metadata, ''), entity_spawn_rowid, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_entity_container FINAL
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- entity_interaction
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 70, toUUID('00000000-0000-0000-0000-000000000070'), 0, 'entity_interaction',
    rowid, time, user, wid, x, y, z, type, NULL, NULL, NULL, NULL,
    action, rolled_back, NULL, nullIf(metadata, ''), entity_spawn_rowid, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_entity_interaction FINAL
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- item
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 80, toUUID('00000000-0000-0000-0000-000000000080'), 0, 'item',
    rowid, time, user, wid, x, y, z, type, NULL, nullIf(data, ''), NULL, NULL,
    action, rolled_back, toInt32(amount), NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_item FINAL
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- entity
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 90, toUUID('00000000-0000-0000-0000-000000000090'), 0, 'entity',
    rowid, time, NULL, 0, 0, NULL, 0, NULL, NULL, nullIf(data, ''), NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_entity
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- entity_spawn
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 100, toUUID('00000000-0000-0000-0000-000000000100'), 0, 'entity_spawn',
    rowid, time, NULL, wid, toInt32(floor(origin_x)), NULL, toInt32(floor(origin_z)), NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    if(block_rowid = 0, NULL, block_rowid), if(kill_rowid = 0, NULL, kill_rowid),
    if(block_rowid = 0, 0, 1), if(kill_rowid = 0, 0, 1),
    uuid, NULL, current_wid,
    origin_x, origin_y, origin_z, x, y, z, yaw, pitch,
    nullIf(data, ''), if(data = '', 0, 1), removed, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_entity_spawn FINAL
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- entity_map
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 110, toUUID('00000000-0000-0000-0000-000000000110'), 0, 'entity_map',
    rowid, 0, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, id, entity, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_entity_map
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- material_map
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 120, toUUID('00000000-0000-0000-0000-000000000120'), 0, 'material_map',
    rowid, 0, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, id, material, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_material_map
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- blockdata_map
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 130, toUUID('00000000-0000-0000-0000-000000000130'), 0, 'blockdata_map',
    rowid, 0, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, id, NULL, data,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_blockdata_map
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- session
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 140, toUUID('00000000-0000-0000-0000-000000000140'), 0, 'session',
    rowid, time, user, wid, x, y, z, NULL, NULL, NULL, NULL, NULL,
    toUInt8(action), NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_session
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- sign
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 150, toUUID('00000000-0000-0000-0000-000000000150'), 0, 'sign',
    rowid, time, user, wid, x, y, z, NULL, NULL, NULL, NULL, NULL,
    action, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, color, color_secondary, data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
FROM kostya.co_sign
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- skull
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 160, toUUID('00000000-0000-0000-0000-000000000160'), 0, 'skull',
    rowid, time, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, owner, skin,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_skull
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- user
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 170, toUUID('00000000-0000-0000-0000-000000000170'), 0, 'user',
    rowid, time, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, if(toString(uuid) = '00000000-0000-0000-0000-000000000000', '', toString(uuid)), user, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_user
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- username_log
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 180, toUUID('00000000-0000-0000-0000-000000000180'), 0, 'username_log',
    rowid, time, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, if(toString(uuid) = '00000000-0000-0000-0000-000000000000', '', toString(uuid)), user, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_username_log
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- world
INSERT INTO coreprotect_playpro.co_event_data
(
    dataset_id, producer_id, producer_sequence, batch_id, batch_ordinal, family,
    rowid, time, user_id, wid, x, y, z, type, data, payload, meta, blockdata,
    action, rolled_back, amount, metadata, entity_spawn_rowid, id, name, text,
    message, status, database_lock_time, version, block_rowid, kill_rowid,
    block_rowid_present, kill_rowid_present, uuid, user_name, current_wid,
    origin_x, origin_y, origin_z, current_x, current_y, current_z, yaw, pitch,
    entity_data, entity_data_present, removed, color, color_secondary, sign_data,
    waxed, face, line_1, line_2, line_3, line_4, line_5, line_6, line_7, line_8
)
SELECT
    identity.dataset_id, identity.producer_id, 190, toUUID('00000000-0000-0000-0000-000000000190'), 0, 'world',
    rowid, 0, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, id, world, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM kostya.co_world
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity;

-- Preserve allocator high-water marks. The official plugin also reads max(rowid)
-- from event_data on startup, but these rows document the imported compatibility
-- IDs and advance producer_sequence past the one-shot import sequences.
INSERT INTO coreprotect_playpro.co_retention_high_water
    (dataset_id, producer_id, producer_sequence, family, rowid, recorded_at)
SELECT identity.dataset_id, identity.producer_id, 1000, marks.family, marks.rowid, now64(3, 'UTC')
FROM
(
    SELECT 'art_map' AS family, ifNull(max(rowid), 0) AS rowid FROM kostya.co_art_map
    UNION ALL SELECT 'block', ifNull(max(rowid), 0) FROM kostya.co_block
    UNION ALL SELECT 'chat', ifNull(max(rowid), 0) FROM kostya.co_chat
    UNION ALL SELECT 'command', ifNull(max(rowid), 0) FROM kostya.co_command
    UNION ALL SELECT 'container', ifNull(max(rowid), 0) FROM kostya.co_container
    UNION ALL SELECT 'entity_container', ifNull(max(rowid), 0) FROM kostya.co_entity_container
    UNION ALL SELECT 'entity_interaction', ifNull(max(rowid), 0) FROM kostya.co_entity_interaction
    UNION ALL SELECT 'item', ifNull(max(rowid), 0) FROM kostya.co_item
    UNION ALL SELECT 'entity', ifNull(max(rowid), 0) FROM kostya.co_entity
    UNION ALL SELECT 'entity_spawn', ifNull(max(rowid), 0) FROM kostya.co_entity_spawn
    UNION ALL SELECT 'entity_map', ifNull(max(rowid), 0) FROM kostya.co_entity_map
    UNION ALL SELECT 'material_map', ifNull(max(rowid), 0) FROM kostya.co_material_map
    UNION ALL SELECT 'blockdata_map', ifNull(max(rowid), 0) FROM kostya.co_blockdata_map
    UNION ALL SELECT 'session', ifNull(max(rowid), 0) FROM kostya.co_session
    UNION ALL SELECT 'sign', ifNull(max(rowid), 0) FROM kostya.co_sign
    UNION ALL SELECT 'skull', ifNull(max(rowid), 0) FROM kostya.co_skull
    UNION ALL SELECT 'user', ifNull(max(rowid), 0) FROM kostya.co_user
    UNION ALL SELECT 'username_log', ifNull(max(rowid), 0) FROM kostya.co_username_log
    UNION ALL SELECT 'world', ifNull(max(rowid), 0) FROM kostya.co_world
) AS marks
CROSS JOIN (SELECT any(dataset_id) AS dataset_id, any(producer_id) AS producer_id FROM coreprotect_playpro.co_storage_metadata) AS identity
WHERE marks.rowid > 0;

SELECT 'target event rows after migration' AS check_name, family, count()
FROM coreprotect_playpro.co_event_data
GROUP BY family
ORDER BY family;

SELECT 'target high-water rows after migration' AS check_name, family, max(rowid)
FROM coreprotect_playpro.co_retention_high_water
GROUP BY family
ORDER BY family;

SELECT
    source.family,
    source.rows AS source_rows,
    ifNull(target.rows, 0) AS target_rows,
    if(source.rows = ifNull(target.rows, 0), 'OK', 'MISMATCH') AS status
FROM
(
    SELECT 'art_map' AS family, count() AS rows FROM kostya.co_art_map
    UNION ALL SELECT 'block', count() FROM kostya.co_block FINAL
    UNION ALL SELECT 'chat', count() FROM kostya.co_chat
    UNION ALL SELECT 'command', count() FROM kostya.co_command
    UNION ALL SELECT 'container', count() FROM kostya.co_container FINAL
    UNION ALL SELECT 'entity_container', count() FROM kostya.co_entity_container FINAL
    UNION ALL SELECT 'entity_interaction', count() FROM kostya.co_entity_interaction FINAL
    UNION ALL SELECT 'item', count() FROM kostya.co_item FINAL
    UNION ALL SELECT 'entity', count() FROM kostya.co_entity
    UNION ALL SELECT 'entity_spawn', count() FROM kostya.co_entity_spawn FINAL
    UNION ALL SELECT 'entity_map', count() FROM kostya.co_entity_map
    UNION ALL SELECT 'material_map', count() FROM kostya.co_material_map
    UNION ALL SELECT 'blockdata_map', count() FROM kostya.co_blockdata_map
    UNION ALL SELECT 'session', count() FROM kostya.co_session
    UNION ALL SELECT 'sign', count() FROM kostya.co_sign
    UNION ALL SELECT 'skull', count() FROM kostya.co_skull
    UNION ALL SELECT 'user', count() FROM kostya.co_user
    UNION ALL SELECT 'username_log', count() FROM kostya.co_username_log
    UNION ALL SELECT 'world', count() FROM kostya.co_world
) AS source
LEFT JOIN
(
    SELECT family, count() AS rows
    FROM coreprotect_playpro.co_event_data
    GROUP BY family
) AS target ON target.family = source.family
ORDER BY source.family;
