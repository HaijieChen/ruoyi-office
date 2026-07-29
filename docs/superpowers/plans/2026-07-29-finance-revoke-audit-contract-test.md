# Finance Revoke Audit Contract Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align `FinanceReceiptClaimControllerContractTest` with the confirmed 11-field revoke-audit response contract without changing production behavior.

**Architecture:** Treat the existing VO, controller display mapping, and frontend `AuditLog` usage as the authoritative API contract introduced by `40806d70e`. Change only the stale reflection-based contract test so it pins the complete ordered field set and Java types.

**Tech Stack:** Java 17, JUnit 5, Maven Surefire

## Global Constraints

- Preserve all 11 current response fields and their declaration order.
- Do not modify production VO, controller, frontend, database, or endpoint paths.
- Keep an exact-field assertion; do not weaken it to subset matching.
- Push only after the focused test, full finance-module test, and `git diff --check` pass.

---

### Task 1: Align the revoke-audit response contract test

**Files:**
- Modify: `yudao-module-finance/yudao-module-finance-server/src/test/java/cn/iocoder/yudao/module/finance/service/claim/FinanceReceiptClaimControllerContractTest.java`
- Reference: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/claim/vo/FinanceReceiptClaimRevokeAuditRespVO.java`
- Reference: `docs/superpowers/specs/2026-07-29-finance-revoke-audit-contract-test-design.md`

**Interfaces:**
- Consumes: `FinanceReceiptClaimRevokeAuditRespVO` field declarations.
- Produces: a JUnit contract that requires the exact 11-field ordered response and validates every field type.

- [x] **Step 1: Run the existing test to verify the RED baseline**

Run:

```bash
mvn -pl yudao-module-finance/yudao-module-finance-server \
  -Dtest=FinanceReceiptClaimControllerContractTest test
```

Expected: one failure showing expected 5 fields and actual 11 fields in `revokeAuditRespVOShouldExposeOnlyImmutableAuditFields`.

- [x] **Step 2: Update the exact field and type contract**

Rename the field-list test and replace its assertion with:

```java
@Test
void revokeAuditRespVOShouldExposePersistedAndDisplayFields() {
    assertEquals(List.of(
                    "id", "claimId", "reviewerId", "reviewerName",
                    "operatorId", "operatorName", "revokeTime", "revokeReason",
                    "reason", "createTime", "action"),
            Stream.of(FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName).toList());
}
```

Extend `revokeAuditRespVOShouldHaveCorrectFieldTypes` with these exact assertions:

```java
assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("reviewerName").getType());
assertEquals(Long.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("operatorId").getType());
assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("operatorName").getType());
assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("reason").getType());
assertEquals(LocalDateTime.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("createTime").getType());
assertEquals(String.class, FinanceReceiptClaimRevokeAuditRespVO.class.getDeclaredField("action").getType());
```

- [x] **Step 3: Run the focused test to verify GREEN**

Run:

```bash
mvn -pl yudao-module-finance/yudao-module-finance-server \
  -Dtest=FinanceReceiptClaimControllerContractTest test
```

Expected: 3 tests, 0 failures, 0 errors.

- [x] **Step 4: Run the complete finance-module regression**

Run:

```bash
mvn -pl yudao-module-finance/yudao-module-finance-server -am test
```

Expected: reactor `BUILD SUCCESS`; all finance-module tests pass with 0 failures and 0 errors.

- [x] **Step 5: Verify and commit the implementation**

Run:

```bash
git diff --check
git diff -- yudao-module-finance/yudao-module-finance-server/src/test/java/cn/iocoder/yudao/module/finance/service/claim/FinanceReceiptClaimControllerContractTest.java
```

Expected: no whitespace errors; diff contains only the confirmed test-contract update.

Commit:

```bash
git add yudao-module-finance/yudao-module-finance-server/src/test/java/cn/iocoder/yudao/module/finance/service/claim/FinanceReceiptClaimControllerContractTest.java \
  docs/superpowers/plans/2026-07-29-finance-revoke-audit-contract-test.md
git commit -m "test(finance): align revoke audit response contract"
```

- [x] **Step 6: Fast-forward `codeup/dev` and verify the remote ref**

Run:

```bash
git fetch codeup dev
git merge-base --is-ancestor codeup/dev HEAD
git push codeup HEAD:dev
git ls-remote codeup refs/heads/dev
```

Expected: the ancestry check exits 0, push is fast-forward, and the remote SHA equals local `HEAD`.
