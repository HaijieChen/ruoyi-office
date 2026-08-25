-- 允许邮箱 MFA（与 TOTP 并列）；checksum = SHA256(ARMED|REQUIRED|EMAIL,TOTP|1|1)
UPDATE system_mfa_control_state
SET global_allowed_factors = 'EMAIL,TOTP',
    checksum = '4527555108846a3cac5a633c5b9d10752677ea194a46529bd3ad48e930f07ba0',
    update_time = NOW()
WHERE id = 1 AND deleted = 0;
