# OA Dynamic Form Data Source and Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add secure configurable SQL data sources and reusable component linkage to the existing dynamic-form designer, then configure the OA forms and 15 workflow models described by the 318 workbook without modifying the BPMN engine.

**Architecture:** The backend stores versioned data-source definitions, validates read-only parameterized SQL, and executes published versions through a dedicated read-only connection. The frontend adds a generic remote data-source component and a deterministic linkage resolver to form-create. OA forms reference published data-source codes; existing simple/BPMN designers configure process behavior. Published v1 models use `admin` (user ID 1) only as an explicit placeholder approver that administrators must replace before production promotion.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Spring JDBC NamedParameterJdbcTemplate, JSqlParser, H2/MySQL, Vue 3, TypeScript, form-create, Vben Admin, Vitest, Flowable BPMN.

## Global Constraints

- Query only the current platform database; do not add external database connectivity.
- Only one parameterized SELECT or WITH ... SELECT statement is executable.
- SQL never reaches runtime form users or runtime API responses; only administrators with data-source edit permission may read it in the management editor. Platform API data sources accept only relative allow-listed paths, and the new component does not execute browser-provided URLs or parser functions.
- Data-source versions are immutable after publication.
- Default query timeout is 3 seconds and default maximum result size is 200 rows.
- BPMN/Flowable engine code is not modified.
- Business approvers are not hard-coded in application code. Runtime v1 models temporarily use `admin` (user ID 1) as a clearly documented placeholder so deployment and integration validation can complete; administrators must replace every placeholder before production promotion.
- Existing running process instances and historical form values remain readable.
- Preserve unrelated local changes in both repositories.
- Backend repository: /Users/chenhaijie/workspace/3dm/royi-oa.
- Frontend workspace: /Users/chenhaijie/workspace/3dm/royi-oa/ruoyi-office-vben (same Git branch as backend).

## File Structure

Backend:

- sql/mysql/bpm_form_data_source.sql: schema, indexes, menu permissions.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmFormDataSourceController.java: management and runtime API.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/: request and response types.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmFormDataSourceDO.java, BpmFormDataSourceVersionDO.java, BpmFormDataSourceLogDO.java: persistence.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceMapper.java, BpmFormDataSourceVersionMapper.java, BpmFormDataSourceLogMapper.java: queries.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/: SQL validator, context resolver, read-only executor.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceService.java and BpmFormDataSourceExecutionService.java: lifecycle and execution.
- yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormLinkageValidator.java: form-save validation.

Frontend:

- apps/web-antd/src/api/bpm/form-data-source/index.ts: API client.
- apps/web-antd/src/views/bpm/form-data-source/: management UI.
- apps/web-antd/src/components/form-create/components/remote-data-source-select.vue: runtime selector.
- apps/web-antd/src/components/form-create/data-source/expression.ts: safe path binding.
- apps/web-antd/src/components/form-create/data-source/linkage.ts: dependency graph and actions.
- apps/web-antd/src/components/form-create/rules/use-remote-data-source-rule.ts: designer rule.

Configuration:

- OA forms configured from docs/OA流程优化-318（合同用印流程变动）.xlsx.
- Eight published simple-process models and seven published BPMN models.
- docs/OA流程优化-318-配置验收记录.md: IDs, keys, screenshots, tests, rollback.

---

### Task 1: Add versioned data-source persistence

**Files:**
- Create: sql/mysql/bpm_form_data_source.sql
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmFormDataSourceDO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmFormDataSourceVersionDO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmFormDataSourceLogDO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceMapper.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceVersionMapper.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceLogMapper.java
- Modify: yudao-module-bpm/yudao-module-bpm-server/src/test/resources/sql/create_tables.sql
- Modify: yudao-module-bpm/yudao-module-bpm-server/src/test/resources/sql/clean.sql
- Test: yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceMapperTest.java

**Interfaces:**
- Produces immutable definition/version/log persistence.
- Adds mapper methods selectByCode, selectPublished, selectNextVersion.

- [ ] **Step 1: Write the failing mapper test**

    @Test
    void insertsDefinitionAndPublishedVersion() {
        BpmFormDataSourceDO source = new BpmFormDataSourceDO()
            .setName("可用印章").setCode("oa_available_seals")
            .setType(1).setStatus(0).setPublishedVersion(1);
        mapper.insert(source);
        versionMapper.insert(publishedVersion(source.getId(), 1));
        assertEquals("oa_available_seals", mapper.selectById(source.getId()).getCode());
        assertEquals(1, versionMapper.selectPublished(source.getId()).getVersion());
    }

- [ ] **Step 2: Run RED**

    mvn -pl yudao-module-bpm/yudao-module-bpm-server -am       -Dtest=BpmFormDataSourceMapperTest       -Dsurefire.failIfNoSpecifiedTests=false test

Expected: compilation fails because objects and mappers do not exist.

- [ ] **Step 3: Implement three tables**

bpm_form_data_source fields: id, name, code, type (SQL, DICT, PLATFORM_API), status, published_version, BaseDO fields, tenant_id. Unique (code, tenant_id, deleted).

bpm_form_data_source_version fields: data_source_id, version, status, source_config, parameter_schema, result_schema, label_field, value_field, pageable, max_rows, timeout_seconds, cache_seconds, BaseDO fields, tenant_id. source_config stores exactly one typed configuration: SQL template, dict type, or relative platform API path/method. Unique (data_source_id, version, deleted).

bpm_form_data_source_log fields: source/version, form/process context, user, parameter_digest, row_count, duration_ms, success, error_code, BaseDO fields, tenant_id. Index (data_source_id, create_time).

- [ ] **Step 4: Run GREEN and all mapper tests**
- [ ] **Step 5: Commit**

    git add sql/mysql/bpm_form_data_source.sql yudao-module-bpm/yudao-module-bpm-server/src
    git commit -m "feat(bpm): add form data source persistence"

---

### Task 2: Validate SQL and resolve named parameters

**Files:**
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceSqlValidator.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceParameter.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceContextResolver.java
- Modify: yudao-module-bpm/yudao-module-bpm-api/src/main/java/cn/iocoder/yudao/module/bpm/enums/ErrorCodeConstants.java
- Test: yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceSqlValidatorTest.java
- Test: yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceContextResolverTest.java

**Interfaces:**
- Produces Set<String> validateAndExtractParameters(String sql).
- Produces Map<String,Object> resolve(Map<String,Object> request, LoginUser user).

- [ ] **Step 1: Write failing safety tests**

    @ParameterizedTest
    @ValueSource(strings = {
      "UPDATE oa_seal SET status=1", "DELETE FROM oa_seal",
      "SELECT 1; SELECT 2", "SELECT * FROM oa_seal -- bypass",
      "SELECT LOAD_FILE('/etc/passwd')", "CALL dangerous_proc()"
    })
    void rejectsUnsafeSql(String sql) {
        assertThrows(ServiceException.class,
            () -> validator.validateAndExtractParameters(sql));
    }

Also assert a SELECT and WITH SELECT return companyId and tenantId parameters.

- [ ] **Step 2: Run RED**
- [ ] **Step 3: Implement JSqlParser AST validation**

Reject blank SQL, semicolons, comments, non-Select statements, LOAD_FILE, INTO_OUTFILE, SLEEP, BENCHMARK, GET_LOCK, RELEASE_LOCK. Extract colon-named parameters in stable order.

- [ ] **Step 4: Add error codes 1_009_010_010 through 1_009_010_019**

Cover invalid/read-only SQL, missing/type-mismatched/reserved parameters, unpublished source, timeout/row limit, source in use, dependency cycle, result mapping mismatch.

- [ ] **Step 5: Implement server context**

    context.put("tenantId", loginUser.getTenantId());
    context.put("userId", loginUser.getId());
    context.put("deptId", loginUser.getInfo().get("deptId"));
    context.put("companyId", loginUser.getContext("companyId", Long.class));

Reject browser overrides of reserved names.

- [ ] **Step 6: Run GREEN**
- [ ] **Step 7: Commit**

    git add yudao-module-bpm/yudao-module-bpm-api/src/main/java/cn/iocoder/yudao/module/bpm/enums/ErrorCodeConstants.java       yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource       yudao-module-bpm/yudao-module-bpm-server/src/test
    git commit -m "feat(bpm): validate dynamic form SQL data sources"

---

### Task 3: Execute published SQL through a dedicated read-only connection

**Files:**
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceProperties.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceConfiguration.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceExecutor.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmFormDataSourceProvider.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmSqlDataSourceProvider.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmDictDataSourceProvider.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/framework/datasource/BpmPlatformApiDataSourceProvider.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceExecutionService.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceExecutionServiceImpl.java
- Modify: yudao-server/src/main/resources/application-local.yaml
- Test: yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceExecutionServiceTest.java

**Interfaces:**
- Produces BpmFormDataSourceQueryResult(rows, total, version).
- Selects a provider by definition type: SQL uses read-only JDBC, DICT uses DictDataApi, PLATFORM_API uses a fixed internal base URL and allow-listed relative paths.

- [ ] **Step 1: Write failing execution tests**

Test that a missing required parameter prevents provider invocation. Test SQL maxRows=2 returns two rows and writes a success log. Test dictionary mapping, API path rejection, cache tenant isolation, sensitive result masking, and failure logging without raw values.

- [ ] **Step 2: Run RED**
- [ ] **Step 3: Configure named non-primary read-only pool**

Use BPM_FORM_DS_URL, BPM_FORM_DS_USERNAME, BPM_FORM_DS_PASSWORD environment variables; pool size 2 and connection timeout 3000 ms. Configure BPM_FORM_API_BASE_URL as a fixed local platform base URL. Do not commit passwords or tokens.

- [ ] **Step 4: Implement limits**

    preparedStatement.setMaxRows(maxRows);
    preparedStatement.setQueryTimeout(timeoutSeconds);
    preparedStatement.getConnection().setReadOnly(true);

Validate parameter types/result fields and write audit logs for success/failure. DICT resolves through DictDataApi. PLATFORM_API joins only the fixed internal base URL with an allow-listed relative path, forwards the authenticated authorization and tenant headers server-side, and rejects schemes, hosts, encoded traversal, and redirects. Cache keys include tenant, source code, published version, normalized parameters, and user when the source is user-scoped. Apply configured mask strategies before returning rows and never cache failures.

- [ ] **Step 5: Run GREEN**
- [ ] **Step 6: Commit**

    git add yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm       yudao-module-bpm/yudao-module-bpm-server/src/test       yudao-server/src/main/resources/application-local.yaml
    git commit -m "feat(bpm): execute published form data sources"

---

### Task 4: Add lifecycle and runtime APIs

**Files:**
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmFormDataSourceController.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourceSaveReqVO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourceRespVO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourcePageReqVO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourceVersionSaveReqVO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourceExecuteReqVO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourceExecuteRespVO.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceService.java
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceImpl.java
- Test: yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceTest.java

**Interfaces:**
- Management: create, draft, trial-run, publish, disable, version history.
- Runtime: execute published version by code without returning SQL/schema.

- [ ] **Step 1: Write failing lifecycle test**

    Long sourceId = service.create(sourceReq("oa_available_seals"));
    Long versionId = service.saveDraft(sourceId, validVersionReq());
    service.publish(sourceId, versionId);
    assertEquals(1, service.get(sourceId).getPublishedVersion());
    assertThrows(ServiceException.class,
        () -> service.updatePublishedVersion(versionId, validVersionReq()));

- [ ] **Step 2: Run RED**
- [ ] **Step 3: Implement transitions**

definition created -> draft 1; draft edited in place; draft published immutable; next edit creates N+1; disabled source denies runtime.

- [ ] **Step 4: Add endpoints**

    POST /bpm/form-data-source/create
    PUT  /bpm/form-data-source/update
    GET  /bpm/form-data-source/page
    GET  /bpm/form-data-source/get
    GET  /bpm/form-data-source/simple-list
    POST /bpm/form-data-source/version/save-draft
    POST /bpm/form-data-source/version/trial-run
    POST /bpm/form-data-source/version/publish
    PUT  /bpm/form-data-source/disable
    POST /bpm/form-data-source/execute/{code}

Management uses bpm:form-data-source permissions. Runtime requires login and access to the referencing form.

- [ ] **Step 5: Run BPM tests**
- [ ] **Step 6: Commit**

    git add yudao-module-bpm/yudao-module-bpm-server/src sql/mysql/bpm_form_data_source.sql
    git commit -m "feat(bpm): manage form data source versions"

---

### Task 5: Validate data-source references when forms are saved

**Files:**
- Create: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormLinkageValidator.java
- Modify: yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormServiceImpl.java
- Modify: yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormServiceTest.java

**Interfaces:**
- Consumes decoded form-create field JSON.
- Produces a valid dependency DAG and published data-source references.

- [ ] **Step 1: Add failing tests**

    assertServiceException(
      () -> formService.createForm(formWithCycle("companyId", "sealIds")),
      FORM_DATA_SOURCE_DEPENDENCY_CYCLE);
    assertServiceException(
      () -> formService.createForm(formWithSource("missing_source")),
      FORM_DATA_SOURCE_NOT_PUBLISHED);

- [ ] **Step 2: Run RED**
- [ ] **Step 3: Implement LinkageNode parsing**

    record LinkageNode(
      String field,
      String dataSourceCode,
      Map<String,String> params,
      List<String> dependencies,
      Map<String,String> outputMappings) {}

Validate duplicate fields, published source, required bindings, result mappings, dependency existence, and cycles with WHITE/GRAY/BLACK DFS. Remove the current unconditional duplicate-field validation bypass while preserving legacy components.

- [ ] **Step 4: Run GREEN**
- [ ] **Step 5: Commit**

    git add yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition       yudao-module-bpm/yudao-module-bpm-server/src/test
    git commit -m "feat(bpm): validate dynamic form linkage"

---

### Task 6: Build the data-source management UI

**Files:**
- Create: ruoyi-office-vben/apps/web-antd/src/api/bpm/form-data-source/index.ts
- Create: ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/data.ts
- Create: ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/index.vue
- Create: ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/modules/editor.vue
- Create: ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/modules/trial-run.vue
- Create: ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/modules/version-list.vue
- Test: ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/data.test.ts

**Interfaces:**
- Consumes Task 4 APIs.
- Produces definition/draft/trial/publish/disable/version UI.

- [ ] **Step 1: Write failing normalization test**

    expect(buildVersionPayload({
      maxRows: 9999,
      timeoutSeconds: 99,
      parameters: [{ name: "companyId", type: "LONG", required: true }],
      resultFields: [{ name: "id", type: "LONG" }]
    })).toMatchObject({
      maxRows: 200,
      timeoutSeconds: 3
    });

- [ ] **Step 2: Run RED**

    ./node_modules/.bin/vitest run apps/web-antd/src/views/bpm/form-data-source/data.test.ts --dom

- [ ] **Step 3: Implement typed API and screens**

Trial run accepts explicit parameter values and shows rows/count/duration/validation error. The editor switches between SQL template, dictionary type, and relative platform API path/method configuration according to source type. Platform API configuration rejects schemes, hosts, path traversal, and paths outside the administrator allow-list. Never display server stack traces.

- [ ] **Step 4: Add idempotent menu SQL**

Add 表单数据源 below 流程设置 with query/create/update/publish permissions using INSERT ... SELECT ... WHERE NOT EXISTS and no hard-coded parent ID.

- [ ] **Step 5: Run test and typecheck**

    ./node_modules/.bin/vitest run apps/web-antd/src/views/bpm/form-data-source/data.test.ts --dom
    ./node_modules/.bin/vue-tsc --noEmit --skipLibCheck -p apps/web-antd/tsconfig.json

- [ ] **Step 6: Commit frontend**

    git add apps/web-antd/src/api/bpm/form-data-source apps/web-antd/src/views/bpm/form-data-source
    git commit -m "feat(bpm): add form data source management"

---

### Task 7: Add safe expressions and linkage actions

**Files:**
- Create: ruoyi-office-vben/apps/web-antd/src/components/form-create/data-source/expression.ts
- Create: ruoyi-office-vben/apps/web-antd/src/components/form-create/data-source/linkage.ts
- Test: ruoyi-office-vben/apps/web-antd/src/components/form-create/data-source/expression.test.ts
- Test: ruoyi-office-vben/apps/web-antd/src/components/form-create/data-source/linkage.test.ts

**Interfaces:**
- Produces resolveBinding, buildDependencyOrder, applyDependencyChange.
- Does not use eval or new Function.

- [ ] **Step 1: Write failing resolver tests**

Assert FORM.companyId and FORM.customer.id resolve. Assert alert(1), operators, __proto__, prototype, and constructor are rejected.

- [ ] **Step 2: Run RED**
- [ ] **Step 3: Implement path-only bindings**

Allow only FORM fields, USER id/deptId/companyId, and PROCESS definitionKey/instanceId. Reject calls, operators, mutation, and prototype paths.

- [ ] **Step 4: Write dependency tests**

Assert companyId orders before sealIds before keeperName. Assert clear-and-reload clears the target and mapped fields; keep-and-revalidate retains only values found in the new option set.

- [ ] **Step 5: Run GREEN**
- [ ] **Step 6: Commit**

    git add apps/web-antd/src/components/form-create/data-source
    git commit -m "feat(form): add safe data source linkage engine"

---

### Task 8: Add the generic selector and designer rule

**Files:**
- Create: ruoyi-office-vben/apps/web-antd/src/components/form-create/components/remote-data-source-select.vue
- Create: ruoyi-office-vben/apps/web-antd/src/components/form-create/rules/use-remote-data-source-rule.ts
- Modify: ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.ts
- Modify: ruoyi-office-vben/apps/web-antd/src/components/form-create/typing.ts
- Modify: ruoyi-office-vben/apps/web-antd/src/plugins/form-create/index.ts
- Test: ruoyi-office-vben/apps/web-antd/src/components/form-create/components/remote-data-source-select.test.ts

**Interfaces:**
- Props: dataSourceCode, parameterBindings, labelField, valueField, multiple, pageable, dependencies, onDependencyChange, outputMappings, formCreateInject.
- Emits value and selected record changes; writes configured mapped values through form-create API.

- [ ] **Step 1: Write failing component test**

Mount with companyId=9, value=[1], source oa_available_seals, clear-and-reload. Trigger companyId change. Assert execute called with companyId=9 and value cleared.

- [ ] **Step 2: Run RED**
- [ ] **Step 3: Implement runtime behavior**

Resolve only safe paths; call execute-by-code; debounce search; ignore stale responses; support loading/empty/retry/disabled/single/multiple/pagination; map selected rows; preserve label/value snapshot for history.

- [ ] **Step 4: Register designer component**

Add 数据源选择器 to the system menu. Property rows configure source, bindings, mappings, dependencies, and clear/revalidate/reload behavior. No raw JS editor.

- [ ] **Step 5: Run tests and typecheck**

    ./node_modules/.bin/vitest run apps/web-antd/src/components/form-create --dom
    ./node_modules/.bin/vue-tsc --noEmit --skipLibCheck -p apps/web-antd/tsconfig.json

- [ ] **Step 6: Remove abandoned prototype files**

After confirming zero imports, delete only:
- apps/web-antd/src/views/oa/seal/sealapply/info/form-rules.ts
- apps/web-antd/src/views/oa/seal/sealapply/info/form-rules.test.ts

- [ ] **Step 7: Commit**

    git add apps/web-antd/src/components/form-create apps/web-antd/src/plugins/form-create       apps/web-antd/src/views/oa/seal/sealapply/info/form-rules.ts       apps/web-antd/src/views/oa/seal/sealapply/info/form-rules.test.ts
    git commit -m "feat(form): add configurable data source selector"

---

### Task 9: Deploy code and publish reusable sources

**Files:**
- Create: docs/OA流程优化-318-配置验收记录.md
- Runtime: ruoyi-office-mysql, ruoyi-office-backend, frontend on port 5666.

**Interfaces:**
- Produces live data-source management and published source codes.

- [ ] **Step 1: Back up BPM/Flowable/menu tables and verify gzip integrity**
- [ ] **Step 2: Apply schema and create MySQL account ruoyi_form_reader with SELECT only**
- [ ] **Step 3: Configure BPM_FORM_DS environment variables without storing the password**
- [ ] **Step 4: Run backend tests/build and restart container**

    mvn -pl yudao-module-bpm/yudao-module-bpm-server -am       -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl yudao-server -am -DskipTests package

- [ ] **Step 5: Run frontend tests/typecheck and confirm the management page opens**
- [ ] **Step 6: Create and publish these sources**

| Code | Type | Purpose | Parameters |
| --- | --- | --- | --- |
| system_companies | PLATFORM_API | active companies | tenantId |
| oa_available_seals | SQL | in-stock company seals | companyId, tenantId |
| bpm_approved_processes | SQL | approved prior processes | processDefinitionKey, companyId, userId, tenantId |
| hrm_entry_processes | SQL | approved employee-entry processes | companyId, employeeKeyword, tenantId |
| hrm_active_employees | PLATFORM_API | active employees | companyId, optional deptId, tenantId |
| crm_customers | SQL | customer/supplier archive | optional keyword, tenantId |
| crm_customer_addresses | SQL | customer mailing/tax/bank data | customerId, tenantId |
| asset_employee_assets | SQL | assigned employee assets | employeeId, tenantId |
| bpm_recharge_contracts | SQL | approved active recharge contracts | customerId, effectiveDate, tenantId |
| bpm_rebate_rules | SQL | effective rebate rules | contractId, accountId, effectiveDate, tenantId |
| bpm_recharge_records | SQL | refundable/transferable recharge records | customerId, tenantId |
| bpm_unclaimed_receipts | SQL | receipts with remaining amount | companyId, tenantId |

Also verify a DICT source by publishing one existing OA dictionary type and binding it to a test selector before configuring production forms.

Where no dedicated table exists, query approved Flowable history variables by process key; do not build a parallel business subsystem.

- [ ] **Step 7: Record code/version/row count/timeout/screenshots and verify runtime responses omit SQL**

---

### Task 10: Configure all dynamic forms

**Files:**
- Source: docs/OA流程优化-318（合同用印流程变动）.xlsx
- Update: docs/OA流程优化-318-配置验收记录.md
- Runtime: bpm_form via existing designer.

**Interfaces:**
- Produces form IDs for workflow models.

- [ ] **Step 1: Create/version these forms**

入职申请、离职申请、采购申请、普通用印、业务合同审批、员工劳动合同、付款申请、费用报销、开票申请、API 开票申请、返点比例审批、充值申请、退款/转充、到款认领/确认.

The recharge parent process has no separate human-entry form.

- [ ] **Step 2: Configure source linkages**

主体公司→印章；文件/付款类型→前置流程；入职流程→劳动合同；客户→地址/税务/银行；离职员工→资产；充值客户→合同→返点；退款/转充→充值记录；到款认领→未认领流水.

- [ ] **Step 3: Configure workbook conditions**

Business-contract fields only for contracts; purchase/rental requires prior process; physical delivery requires address; execution-only fields remain locked until handling; substitute-invoice fields are conditional; payment source changes with reason; invoice customer fills tax details; labor contract fills employee/dates; recharge validates selected contract/rebate; refund/transfer fields switch by type.

- [ ] **Step 4: Save/reopen/preview every form**

Verify linkages, requiredness, history replay, upstream changes. Record form ID and source codes. Reject raw URL, SQL, parseFunc, eval, or new Function configuration.

---

### Task 11: Draw and publish eight simple-process models

**Files:**
- Update: docs/OA流程优化-318-配置验收记录.md
- Runtime: existing simple designer.

**Interfaces:**
- Produces eight v1 models whose human nodes use the documented `admin` placeholder approver.

- [ ] **Step 1: Draw exact sequences**

    oa_employee_entry:
      发起人 → 部门主管审批 → 总经理审批 → 实际入职办理 → 更新员工档案触发器 → 结束

    oa_purchase_apply:
      发起人 → 部门主管审批 → 总经理审批 → 财务负责人审批 → 采购执行办理 → 结束

    oa_seal_general:
      发起人 → 部门主管审批 → 财务负责人审批 → 用印执行办理 → 文件归档触发器 → 结束

    oa_payment_apply:
      发起人 → 前置单据条件校验 → 业务负责人审批 → 财务负责人审批 → 总经理审批 → 出纳付款办理 → 结束

    oa_expense_reimbursement:
      发起人 → 业务负责人审批 → 财务负责人审批 → 总经理审批 → 出纳付款办理 → 代付通知触发器 → 结束

    oa_rebate_approval:
      发起人 → 渠道总监审批 → 特殊政策条件分支 → 总经理审批/汇聚 → 规则生效触发器 → 结束

    oa_recharge_apply:
      发起人 → 部门负责人审批 → 渠道总监审批 → 财务充值确认办理 → 更新状态触发器 → 结束

    oa_refund_transfer:
      发起人 → 运营专员审批 → 财务审批 → 渠道总监审批 → 大额退款条件分支 → 副总审批/汇聚 → 执行办理 → 结束

- [ ] **Step 2: Configure field permissions per applicant/finance/execution node**
- [ ] **Step 3: Set `admin` (user ID 1) as placeholder on every human node; deploy and verify v1**
- [ ] **Step 4: Record model IDs, keys, form IDs, screenshots**

---

### Task 12: Draw and publish seven BPMN models

**Files:**
- Update: docs/OA流程优化-318-配置验收记录.md
- Runtime: existing BPMN designer.

**Interfaces:**
- Produces seven valid v1 definitions; human nodes use the documented `admin` placeholder and the hidden orchestration parent has no human node.

- [ ] **Step 1: oa_employee_resignation**

Start → supervisor → parallel work handover/IT/admin/finance confirmations → join → HR completion → employee archive update → End.

- [ ] **Step 2: oa_business_contract**

Start → prior-process validation → business manager → legal → finance → GM → seal execution → archive → expiry-reminder subprocess → End. Reminder timers: 30/15/7 days before end; skip past timers.

- [ ] **Step 3: oa_labor_contract**

Start → entry-process validation → department manager → finance → seal execution → archive → expiry-reminder subprocess → End.

- [ ] **Step 4: oa_invoice_apply**

Start → business → finance → invoice handling → wait for receipt; receipt leads to finance confirmation/notify/end; timer leads to reminder and returns to wait.

- [ ] **Step 5: oa_api_invoice_apply**

Message start(API) → validate fields → finance → invoice handling → return result → message end.

- [ ] **Step 6: oa_receipt_claim**

Import/start → generate receipt records → match business documents → multi-instance claim details → finance confirm; confirm updates amounts/end; revoke rolls back and returns to confirmation.

- [ ] **Step 7: oa_recharge_orchestration**

Start → business-contract child → rebate child → recharge child → receipt-claim child → event gateway; normal end or refund/transfer child then end.

- [ ] **Step 8: Validate/save all models, verify bindings/keys/placeholders, deploy v1 and record definition versions**

---

### Task 13: Administrator handoff and final verification

**Files:**
- Update: docs/OA流程优化-318-配置验收记录.md

**Interfaces:**
- Consumes administrator replacement of the documented placeholder approvers.
- Produces verified local deployment ready for production promotion.

- [ ] **Step 1: Provide all approval-node names grouped by model; administrator replaces every `admin` placeholder with the production candidate rule**
- [ ] **Step 2: Deploy only models passing existing candidate validation; record definition IDs/versions**
- [ ] **Step 3: Run fresh automated verification**

Backend:

    mvn -pl yudao-module-bpm/yudao-module-bpm-server -am       -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl yudao-server -am -DskipTests package

Frontend:

    ./node_modules/.bin/vitest run apps/web-antd/src/components/form-create       apps/web-antd/src/views/bpm/form-data-source --dom
    ./node_modules/.bin/vue-tsc --noEmit --skipLibCheck -p apps/web-antd/tsconfig.json
    ./node_modules/.bin/vite build --mode production

- [ ] **Step 4: Verify SQL injection/read-only/timeout/row-limit/context protections**
- [ ] **Step 5: Verify all configured component linkage chains**
- [ ] **Step 6: Complete one test instance per deployed model, including conditions, joins, timers, triggers, children, attachments, and history**
- [ ] **Step 7: Verify old process instances and the old seal form remain readable; hide old entry only after acceptance**
- [ ] **Step 8: Finalize report with backup checksum, schema, commits, IDs/versions, tests, screenshots, limitations, rollback**

## Self-Review

- [ ] Every approved design requirement maps to a task.
- [ ] BPMN engine code remains untouched.
- [ ] SQL execution is server-only, parameterized, read-only, limited, and audited.
- [ ] Published versions and historical values remain immutable/readable.
- [ ] Both repositories have explicit tests and build commands.
- [ ] All 8 simple and 7 BPMN models have exact node sequences.
- [ ] Production approvers remain administrator-configured; `admin` is only a documented v1 placeholder.
- [ ] Unrelated working-tree changes are preserved.
