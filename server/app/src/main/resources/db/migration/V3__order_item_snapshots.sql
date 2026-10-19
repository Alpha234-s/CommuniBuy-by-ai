-- Existing titles/descriptions were never captured: do not fabricate historical snapshots from current catalog.
ALTER TABLE orders ADD item_name VARCHAR(128), ADD item_description TEXT,
 ADD snapshot_source VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
