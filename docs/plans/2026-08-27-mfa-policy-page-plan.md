# MFA 策略页 + 超管强制 MFA

## Goal

管理员可在系统管理页开关普通用户的登录 MFA；**超级管理员（角色 `super_admin`）始终强制 MFA，不受该页控制。**

## Product

- 页面：系统管理 → MFA 策略。模式 OFF / OPTIONAL / REQUIRED；因子 TOTP、SMS、EMAIL。
- OPTIONAL/REQUIRED 至少选一种因子。
- 页内固定说明：超级管理员登录始终要 MFA。
- 不改租户级策略页；不改个人中心绑定流程。

## How

1. `MfaTokenIssuanceFacadeImpl.evaluateNeed`：若 `RoleService.hasAnySuperAdmin(PermissionService.getUserRoleIdListByUserId(userId))`，按 REQUIRED 处理（无因子则 enrollment）。`refresh` 对超管同样按非 OFF 做 epoch 校验。
2. 现有 `GET/PUT /system/mfa-policy` 不变。
3. 前端 `views/system/mfa/policy/index.vue` 调已有 API。
4. 幂等 SQL：菜单 `system/mfa/policy/index`，权限 `system:mfa-policy:query|update`，父级 `/system`。

## Verify

- 单测：全局 OFF 时普通用户仍直登；超管返回 MFA_REQUIRED/ENROLLMENT。
- 页面保存 OFF 后，非超管不再挑战；超管仍挑战。
