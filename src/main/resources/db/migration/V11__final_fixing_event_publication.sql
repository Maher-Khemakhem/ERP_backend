ALTER TABLE platform.event_publication ALTER COLUMN serialized_event TYPE TEXT;
ALTER TABLE platform.event_publication ALTER COLUMN listener_id TYPE VARCHAR(512);
ALTER TABLE platform.event_publication ALTER COLUMN event_type TYPE VARCHAR(512);

ALTER TABLE public.event_publication ALTER COLUMN serialized_event TYPE TEXT;
ALTER TABLE public.event_publication ALTER COLUMN listener_id TYPE VARCHAR(512);
ALTER TABLE public.event_publication ALTER COLUMN event_type TYPE VARCHAR(512);