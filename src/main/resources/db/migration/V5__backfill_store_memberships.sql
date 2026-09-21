ALTER TABLE platform.store_users
    ADD CONSTRAINT fk_store_users_store
    FOREIGN KEY (store_id) REFERENCES platform.stores(id);

ALTER TABLE platform.store_users
    ADD CONSTRAINT fk_store_users_user
    FOREIGN KEY (user_id) REFERENCES public.app_user(id);

INSERT INTO platform.store_users (store_id, user_id, role)
SELECT '00000000-0000-0000-0000-000000000001', user_id, role
FROM public.app_user_roles
ON CONFLICT (store_id, user_id, role) DO NOTHING;
