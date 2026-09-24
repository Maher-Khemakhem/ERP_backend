ALTER TABLE public.app_user_roles
    DROP CONSTRAINT IF EXISTS app_user_roles_role_check;

ALTER TABLE public.app_user_roles
    ADD CONSTRAINT app_user_roles_role_check
    CHECK (role IN (
        'ADMIN',
        'MANAGER',
        'CASHIER',
        'INVENTORY_MANAGER',
        'ACCOUNTANT',
        'CUSTOMER'
    ));
