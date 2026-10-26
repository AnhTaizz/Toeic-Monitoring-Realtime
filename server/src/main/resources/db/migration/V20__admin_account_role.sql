-- Existing rows and all exam/admission tables remain unchanged.
ALTER TABLE user_accounts DROP CONSTRAINT user_accounts_role_check;
ALTER TABLE user_accounts ADD CONSTRAINT user_accounts_role_check
    CHECK (role IN ('CANDIDATE', 'PROCTOR', 'ADMIN'));
