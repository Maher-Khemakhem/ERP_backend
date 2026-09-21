ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE TEXT;
ALTER TABLE event_publication ALTER COLUMN listener_id TYPE VARCHAR(512);
ALTER TABLE event_publication ALTER COLUMN event_type TYPE VARCHAR(512);