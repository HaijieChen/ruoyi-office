---
title: New employee number defaults to max-plus-one
type: feat
date: 2026-09-04
topic: employee-no-next-default
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-brainstorm
execution: code
---

# New employee number defaults to max-plus-one

## Goal Capsule

- Objective: HR can open the new-employee form and see the next employee number already filled, then keep or change it before save.
- Means: Preview next number from the tenant's max numeric employee number (keep width), unlock the create-form field, keep save-time assignment when empty.
- Product authority: This conversation. Import blank numbers and editing an existing employee's number are not active scope.
- Open blockers: None.

## Product Contract

### Summary

On create, the employee-number field shows the next number derived from the current tenant's largest numeric employee number, keeping that number's width, and the field is editable.
Save still enforces uniqueness.

### Problem Frame

Today the create form disables the employee-number field and only assigns an eight-digit series at save.
HR cannot see the next number up front, and cannot type a short number such as `0072`.

### Key Decisions

- **KD1. Numeric max plus one, keep width.** `(session-settled: user-directed — chosen over the existing eight-digit 10000000 series: HR wants 0071 to suggest 0072, and 10000083 to suggest 10000084.)` Governs R2, R3.
- **KD2. Create only.** Editing an existing employee number stays locked. Governs R5.
- **KD3. Import blanks stay blank.** Roster import does not auto-fill empty numbers. Governs R6.

### Requirements

**Create form**

- R1. Opening the new-employee form fills employee number with the next suggested value and leaves the field editable.
- R2. The suggestion is one greater than the largest purely numeric employee number in the current tenant, padded to that value's digit width. Covers KD1.
- R3. If that increment no longer fits the old width, the suggestion uses the natural width of the new number (`9999` -> `10000`). Covers KD1.
- R4. The operator may change or clear the suggestion before save. A non-empty value is saved as typed. An empty value is assigned the same next-number rule at save.

**Edit and import**

- R5. Viewing or editing an existing employee keeps the number read-only.
- R6. Roster import still does not generate numbers for blank employee-number cells.

**Integrity**

- R7. Duplicate active employee numbers in the same tenant are still rejected.
- R8. Non-numeric employee numbers are ignored when computing the maximum.

### Actors

- A1. HR operator creating an employee archive.
- A2. HR operator editing or importing archives (must not gain extra number-edit or import-autofill behavior).

### Key Flows

- F1. Create with default. Operator opens new employee, sees the suggested number, saves without editing. Covers R1, R2, R4.
- F2. Create with override. Operator replaces the suggestion (including a shorter number) and saves. Covers R4, R7.
- F3. Mixed existing numbers. Tenant has both `0071` and `10000083`. Suggestion is `10000084`. Covers R2.

### Acceptance Examples

- AE1. Covers R2. Tenant max numeric number is `0071`. Create form shows `0072`.
- AE2. Covers R2. Tenant max numeric number is `10000083`. Create form shows `10000084`.
- AE3. Covers R3. Tenant max numeric number is `9999`. Create form shows `10000`.
- AE4. Covers R4, R7. Operator changes the suggestion to an in-use number. Save is rejected.
- AE5. Covers R5. Edit form for an existing employee shows the stored number and does not allow changing it.
- AE6. Covers R6. Import row with a blank employee number remains blank after import.

### Scope Boundaries

- In: new-employee form default and editability; save-time assignment when the field is empty; uniqueness.
- Out: changing numbers on existing employees; auto-filling blank import cells; changing how generated system usernames work.

### Success Criteria

- SC1. An HR operator can create an employee using the suggested number without typing it.
- SC2. An HR operator can type a different unused number and save.
- SC3. The previous import-blank and edit-lock behaviors still hold.

## Planning Contract

### Key Technical Decisions

- KTD1. Pure formatter `EmployeeNoGenerator.next(maxNumeric)` owns width-preserving increment. Empty tenant yields `0001`. Mapper returns the max numeric `employee_no` string, not a Long that jumps at 10000000.
- KTD2. Create-if-empty still calls the same generator. Import `skipAutoEmployeeNo` is unchanged.
- KTD3. Create form loads `GET /hrm/employee-archive/next-employee-no` (create permission). Edit keeps the field disabled.

### Assumptions

- A1. `0001` is acceptable when the tenant has no numeric employee numbers.
- A2. Non-numeric numbers stay stored but never drive the suggestion (R8).

## Implementation Units

### U1. Next-number generator and save path

Files: `yudao-module-hrm/.../EmployeeNoGenerator.java` (already started), `EmployeeMapper.java`, `EmployeeService.java`, `EmployeeServiceImpl.java`, `EmployeeNoGeneratorTest.java`, `EmployeeServiceImplTest.java`, `EmployeeRosterImportTest.java`.

Approach: Replace `selectMaxEmployeeNo()` Long/8-digit jump with max numeric string query. Create empty field uses `EmployeeNoGenerator.next`. Keep import skip.

Tests: `0071` -> `0072`; `10000083` -> `10000084`; `9999` -> `10000`; null -> `0001`; import blank still never calls generator.

### U2. Preview API

Files: `EmployeeController.java`, `EmployeeService.java`.

Approach: `GET /hrm/employee-archive/next-employee-no` with `hrm:employee-archive:create`. Returns the same string U1 would assign.

Tests: Service/controller-level assertion that preview equals generator output for a stubbed max.

### U3. Create form default and unlock

Files: `ruoyi-office-vben/apps/web-antd/src/api/hrm/employee/index.ts`, `views/hrm/employee/info/data.ts`, `views/hrm/employee/info/index.vue`.

Approach: Unlock employeeNo on create only. Help text: default is max-plus-one, editable. On create mount, fetch U2 and set the field. Do not clear a typed value on save. Edit remains disabled.

## Verification Contract

- `mvn -pl yudao-module-hrm/yudao-module-hrm-server -am -Dtest=EmployeeNoGeneratorTest,EmployeeServiceImplTest,EmployeeRosterImportTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Manual: open 新增员工档案, confirm suggested number, override, save; open edit, number locked.

## Definition of Done

- R1–R8 covered by U1–U3.
- Import blank and edit-lock regressions still pass.
- Suggested next step after local green: push `codeup/test` and run `test-sdm-oa` plus `test-sdm-oa-web`.
