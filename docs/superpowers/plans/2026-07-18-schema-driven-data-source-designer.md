# Schema 驱动的表单数据源设计器实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 `RemoteDataSourceSelect` 的参数、结果、表单字段配置全部改成由已发布 Schema 和当前表单驱动的严格下拉，并为 Schema 字段补充中文名称，同时保持运行时配置协议和数据源安全边界不变。

**Architecture:** 后端新增一个只读的已发布元数据接口，只返回启用数据源当前发布版本的安全字段信息；数据源草稿保存继续走现有版本服务，但要求每个 Schema 字段具有中文 `label`。前端新增应用自有的设计器属性组件，通过 Form Create 的 `designerForm.component` 注册，并使用设计器公开的规则 API 枚举当前表单；属性组件只写入现有的扁平 props，不改变运行时 `RemoteDataSourceSelect` 协议。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、Vue 3、TypeScript、Ant Design Vue、Form Create Designer、Vitest、Vue Test Utils。

---

## 开始前约束

- 当前分支为 `codex/oa-platform-production`，主工作区为 `/Users/chenhaijie/workspace/3dm/royi-oa`。
- 保留已有未提交修改，尤其是：
  - `ruoyi-office-vben/apps/web-antd/.env.development`
  - `ruoyi-office-vben/apps/web-antd/src/components/form-create/components/remote-data-source-select.vue`
  - `ruoyi-office-vben/apps/web-antd/src/components/form-create/components/remote-data-source-select.test.ts`
- 不修改 `node_modules`，不改变 `RemoteDataSourceSelect` 的运行时 props 结构。
- 每个任务遵循 RED → GREEN → REFACTOR；只提交该任务相关文件。
- 后端 Mockito/Byte Buddy 测试在受限沙箱中可能于测试体执行前自附加失败；出现该错误时使用可信执行重跑同一 Maven 命令，并将环境失败与业务测试结果分开记录。

## Task 1：为 Schema 中文名称建立前端保存契约

**Files:**

- Modify: `ruoyi-office-vben/apps/web-antd/src/api/bpm/form-data-source/index.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/data.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/data.test.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/modules/editor.vue`

### Step 1：先写失败测试

在 `data.test.ts` 增加以下用例：

```ts
it('normalizes and persists trimmed schema labels', () => {
  const payload = buildVersionPayload({
    config: { sql: 'SELECT id FROM demo WHERE tenant_id = :tenantId' },
    pageable: false,
    parameters: [
      { label: ' 当前租户 ', name: 'tenantId', required: true, type: 'long' },
    ],
    resultFields: [{ label: ' 编号 ', name: 'id', type: 'long' }],
    sourceType: 1,
  });

  expect(JSON.parse(payload.parameterSchema!)[0].label).toBe('当前租户');
  expect(JSON.parse(payload.resultSchema!)[0].label).toBe('编号');
});

it.each(['', '   ', '中'.repeat(65)])('rejects invalid schema label %j', (label) => {
  expect(() => buildVersionPayload({
    config: { sql: 'SELECT id FROM demo WHERE tenant_id = :tenantId' },
    pageable: false,
    parameters: [{ label, name: 'tenantId', type: 'LONG' }],
    resultFields: [{ label: '编号', name: 'id', type: 'LONG' }],
    sourceType: 1,
  })).toThrow(/中文名称/);
});
```

运行并确认失败：

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom apps/web-antd/src/views/bpm/form-data-source/data.test.ts
```

### Step 2：实现类型、标准化和校验

在 API 类型中加入必填字段：

```ts
export interface SchemaField {
  label: string;
  mask?: string;
  name: string;
  required?: boolean;
  type: string;
}
```

在 `data.ts` 中：

- `normalizeSchemaFields` 保留并 trim `label`；
- 新增 `validateSchemaLabels(fields, section)`，要求 trim 后长度为 1–64；
- `buildVersionPayload` 在字段名校验后调用中文名称校验；
- `parseSchemaFields` 对历史缺少 `label` 的字段保留空值；编辑界面用技术 `name` 作为输入框提示，但保存前仍须由用户明确补全中文名称，避免把技术名静默写成中文名称。

### Step 3：修改数据源编辑界面

- 参数 Schema 行顺序：中文名称、参数名、类型、必填、删除；
- 结果 Schema 行顺序：中文名称、字段名、类型、脱敏、删除；
- 新增行默认 `label: ''`；
- 标签字段和值字段的选项显示 `中文名称（技术字段）`；
- `validateSchemaRows` 同时校验中文名称。

### Step 4：运行测试和类型检查

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom apps/web-antd/src/views/bpm/form-data-source/data.test.ts
pnpm --filter @vben/web-antd run typecheck
```

### Step 5：提交

```bash
git add ruoyi-office-vben/apps/web-antd/src/api/bpm/form-data-source/index.ts \
  ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/data.ts \
  ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/data.test.ts \
  ruoyi-office-vben/apps/web-antd/src/views/bpm/form-data-source/modules/editor.vue
git commit -m "feat(bpm): require schema field labels"
```

## Task 2：后端严格校验新草稿中的 Schema 中文名称

**Files:**

- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceImpl.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceTest.java`

### Step 1：先写失败测试

在 `BpmFormDataSourceServiceTest` 增加：

```java
@Test
void saveDraft_rejectsMissingSchemaLabel() {
    BpmFormDataSourceVersionSaveReqVO req = sqlVersionReq(
            "SELECT id, name FROM oa_seal WHERE tenant_id = :tenantId");
    req.setResultSchema("[{\"name\":\"id\",\"type\":\"LONG\"}]");
    when(dataSourceMapper.selectByIdForUpdate(10L)).thenReturn(source(10L, 1));

    assertThrows(ServiceException.class, () -> service.saveDraft(10L, req));
}

@Test
void saveDraft_rejectsBlankOrOverlongSchemaLabel() {
    // 分别构造全空白和 65 字符 label，均断言 BPM_DATA_SOURCE_CONFIG_INVALID。
}
```

同时把现有测试 helper 的 Schema 改为包含 label，例如：

```java
.setParameterSchema("[{\"name\":\"tenantId\",\"label\":\"当前租户\",\"type\":\"LONG\",\"required\":true}]")
.setResultSchema("[{\"name\":\"id\",\"label\":\"编号\",\"type\":\"LONG\"},"
        + "{\"name\":\"name\",\"label\":\"名称\",\"type\":\"STRING\"}]")
```

运行并确认新增用例失败：

```bash
mvn -pl yudao-module-bpm/yudao-module-bpm-server -am \
  -Dtest=BpmFormDataSourceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

### Step 2：实现严格保存、宽松读取所需的解析能力

将内部字段模型扩展为：

```java
@Data
public static class SchemaField {
    private String name;
    private String label;
    private String type;
    private Boolean required;
    private String mask;
    private String maskStrategy;
}
```

把解析函数调整为显式模式：

```java
private static List<SchemaField> parseSchema(String json, boolean requireLabel)
```

- `validateVersion` 使用 `requireLabel = true`；
- 当 `requireLabel` 为 true 时，label trim 后必须为 1–64 字符；
- name、type、mask、重复字段等现有校验原样保留；
- 解析不得修改历史 JSON，只在内存对象中将 label trim；
- 后续已发布元数据读取使用 `requireLabel = false`，兼容历史版本。

### Step 3：运行测试

```bash
mvn -pl yudao-module-bpm/yudao-module-bpm-server -am \
  -Dtest=BpmFormDataSourceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

### Step 4：提交

```bash
git add yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceImpl.java \
  yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceTest.java
git commit -m "feat(bpm): validate schema field labels"
```

## Task 3：新增安全的已发布元数据接口

**Files:**

- Create: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourcePublishedMetadataRespVO.java`
- Create: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourcePublishedMetadata.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceVersionMapper.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceService.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceImpl.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmFormDataSourceController.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceTest.java`
- Modify: `yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmFormDataSourceControllerSecurityTest.java`

### Step 1：先写服务失败测试

覆盖以下场景：

- 启用且有发布指针时，按 `dataSourceId + publishedVersion` 精确读取版本；
- 停用、无发布版本、发布指针找不到对应已发布版本时，返回数据源不存在或版本不存在；
- 历史 Schema 缺少 label 时仍可读取，label 回退为 name；
- 返回值仅包含定义元数据、参数字段、结果字段、labelField、valueField、pageable。

新增 Mapper 方法：

```java
default BpmFormDataSourceVersionDO selectPublishedBySourceIdAndVersion(
        Long dataSourceId, Integer version) {
    return selectOne(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
            .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId)
            .eq(BpmFormDataSourceVersionDO::getVersion, version)
            .eq(BpmFormDataSourceVersionDO::getStatus,
                    BpmFormDataSourceVersionStatusEnum.PUBLISHED.getStatus()));
}
```

### Step 2：定义安全服务模型和响应 VO

服务模型用于承载已解析字段，不暴露执行配置：

```java
public record BpmFormDataSourcePublishedMetadata(
        Long id, String name, String code, Integer type, Integer publishedVersion,
        List<Field> parameterFields, List<Field> resultFields,
        String labelField, String valueField, Boolean pageable) {
    public record Field(String name, String label, String type, Boolean required, String mask) {}
}
```

响应 VO 与上述字段一一对应。禁止加入 `sourceConfig`、原始 `parameterSchema`、原始 `resultSchema`、SQL 或 API 路径。

### Step 3：实现服务方法

在接口加入：

```java
BpmFormDataSourcePublishedMetadata getPublishedMetadata(String code);
```

实现顺序：

1. `selectByCode(code)`；
2. 校验状态启用且 `publishedVersion != null`；
3. 通过新 Mapper 方法读取发布指针对应版本；
4. 使用宽松 `parseSchema(..., false)`；
5. 将空 label 回退为 name；
6. 构建安全元数据对象。

租户边界继续由现有 MyBatis 租户拦截器提供；不要增加忽略租户注解。

### Step 4：实现 Controller 和安全测试

新增：

```java
@GetMapping("/published-metadata")
@Operation(summary = "获得表单设计器可用的已发布数据源元数据",
        description = "仅返回字段 Schema 和选择器元数据，不返回执行配置")
@PreAuthorize("@ss.hasPermission('bpm:form-data-source:query')")
public CommonResult<BpmFormDataSourcePublishedMetadataRespVO> getPublishedMetadata(
        @RequestParam("code")
        @NotBlank @Size(max = 127)
        @Pattern(regexp = "^[a-z][a-z0-9_]*$") String code) {
    return success(BpmFormDataSourcePublishedMetadataRespVO.from(
            dataSourceService.getPublishedMetadata(code)));
}
```

安全测试使用反射断言 query 权限，并断言响应 VO 的字段集合不包含：

```java
Set.of("sourceConfig", "parameterSchema", "resultSchema", "sql", "path")
```

### Step 5：运行后端测试

```bash
mvn -pl yudao-module-bpm/yudao-module-bpm-server -am \
  -Dtest=BpmFormDataSourceServiceTest,BpmFormDataSourceControllerSecurityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Step 6：提交

```bash
git add yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/datasource/BpmFormDataSourcePublishedMetadataRespVO.java \
  yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourcePublishedMetadata.java \
  yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/dal/mysql/definition/BpmFormDataSourceVersionMapper.java \
  yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceService.java \
  yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceImpl.java \
  yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmFormDataSourceController.java \
  yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/service/definition/BpmFormDataSourceServiceTest.java \
  yudao-module-bpm/yudao-module-bpm-server/src/test/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmFormDataSourceControllerSecurityTest.java
git commit -m "feat(bpm): expose safe published data source metadata"
```

## Task 4：实现设计器的纯函数模型和兼容清理规则

**Files:**

- Create: `ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-model.ts`
- Create: `ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-model.test.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/api/bpm/form-data-source/index.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.test.ts`

### Step 1：扩展前端元数据 API

新增类型和请求：

```ts
export interface PublishedMetadata {
  code: string;
  id: number;
  labelField?: string;
  name: string;
  pageable: boolean;
  parameterFields: SchemaField[];
  publishedVersion: number;
  resultFields: SchemaField[];
  type: number;
  valueField?: string;
}

export function getPublishedDataSourceMetadata(code: string) {
  return requestClient.get<BpmFormDataSourceApi.PublishedMetadata>(
    '/bpm/form-data-source/published-metadata',
    { params: { code } },
  );
}
```

### Step 2：先写纯函数失败测试

覆盖：

- Schema 选项文本为 `中文名称（字段名 · 类型 · 必填）`；
- `tenantId/userId/deptId/companyId` 进入“系统自动注入”说明但不进入可绑定参数；
- 搜索、页码、每页条数参数不能重复占用；
- 表单字段递归遍历 `children`、`props.rule`、`props.columns[].rule`、`control[].rule`；
- 目标字段排除当前组件和全部 `RemoteDataSourceSelect`；
- 表达式只生成 `FORM.*`、允许的 `USER.*` 和 `PROCESS.*`；
- 切换数据源只保留新 Schema 中仍存在且用途不冲突的配置；
- 旧配置引用不存在字段时返回 invalid 标记，不静默删除。

计划导出接口：

```ts
export function buildSchemaOptions(fields: SchemaField[]): SelectOption[];
export function collectDesignerFormFields(rules: unknown[]): DesignerField[];
export function buildBindingExpressionGroups(fields: DesignerField[]): OptionGroup[];
export function getBindableParameters(metadata: PublishedMetadata, props: RemoteProps): SchemaField[];
export function validateRemoteProps(metadata: PublishedMetadata, fields: DesignerField[], props: RemoteProps): Issue[];
export function reconcileRemoteProps(previous: RemoteProps, next: PublishedMetadata): RemoteProps;
```

运行并确认失败：

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom \
  apps/web-antd/src/components/form-create/designer/remote-data-source-model.test.ts \
  apps/web-antd/src/components/form-create/helpers.test.ts
```

### Step 3：实现递归枚举和严格模型

- 复用 `data-source/expression.ts` 中的允许表达式边界，不复制另一套宽松语法；
- 将 `parseFormFields` 的递归容器能力补齐到与运行时 hydration 一致；
- 只把具有稳定 `field` 和可编辑语义的规则作为普通表单字段；
- 返回值均使用不可变的新对象，避免在确认切换前修改当前规则。

### Step 4：运行测试

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom \
  apps/web-antd/src/components/form-create/designer/remote-data-source-model.test.ts \
  apps/web-antd/src/components/form-create/helpers.test.ts
```

### Step 5：提交

```bash
git add ruoyi-office-vben/apps/web-antd/src/api/bpm/form-data-source/index.ts \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-model.ts \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-model.test.ts \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.ts \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.test.ts
git commit -m "feat(bpm): model strict data source designer options"
```

## Task 5：实现应用自有的严格数据源属性编辑器

**Files:**

- Create: `ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.vue`
- Create: `ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.test.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/components/form-create/rules/use-remote-data-source-rule.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.ts`
- Modify: `ruoyi-office-vben/apps/web-antd/src/plugins/form-create/index.ts`

### Step 1：先写组件失败测试

用 Vue Test Utils stub 元数据 API、路由和设计器 API，覆盖：

- 所有 Schema/表单字段控件均为 `Select`，页面不存在可输入技术字段名的 `Input`；
- 参数行和结果映射行可添加/删除，重复项禁用；
- 无字段时显示具体空状态文案，而不是“暂无数据”；
- 元数据加载失败保留旧配置、显示重试、禁止新增选择；
- 非法历史值显示“字段已不存在”，不能当作新选项；
- 切换数据源先确认，确认后用 `reconcileRemoteProps` 一次性替换兼容配置，取消则不变；
- “刷新”绕过缓存重新请求；
- “管理数据源”执行 `router.push({ name: 'BpmFormDataSource' })`，由现有多页签路由保留当前表单设计页。

运行并确认失败：

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom \
  apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.test.ts
```

### Step 2：注册设计器属性组件

在 Form Create 插件中使用公开导出注册：

```ts
import FcDesigner, { designerForm } from '@form-create/antd-designer';
import RemoteDataSourceConfigEditor from '#/components/form-create/designer/remote-data-source-config-editor.vue';

designerForm.component(
  'RemoteDataSourceConfigEditor',
  RemoteDataSourceConfigEditor,
);
```

不要修改第三方 `TableOptions`。

### Step 3：让规则只使用自有属性编辑器

将 `useRemoteDataSourceRule` 改为接收设计器 ref：

```ts
export function useRemoteDataSourceRule(designer: Ref) { /* ... */ }
```

属性规则保留 `makeRequiredRule()`，其余数据源相关输入替换为一个应用自有组件：

```ts
{
  type: 'RemoteDataSourceConfigEditor',
  field: 'dataSourceCode',
  title: '数据源配置',
  props: {
    getActiveRule: () => api.activeRule,
    getFormRules: () => designer.value?.getRule?.() ?? [],
  },
}
```

组件的 `modelValue` 仍对应 `dataSourceCode`；其他控件通过 `getActiveRule().props` 写入现有属性：

```ts
parameterBindings, outputMappings, dependencies, multiple,
labelField, valueField, searchParamName, pageable,
pageNoParamName, pageSizeParamName, pageSize,
snapshotField, onDependencyChange
```

直接替换属性时生成新对象/数组，让设计器深度 watcher 记录撤销历史并刷新属性表单。不得写入新的嵌套运行时配置对象。

### Step 4：实现加载、缓存和错误状态

- 组件挂载时加载精简列表；选中 code 后加载发布元数据；
- 模块级缓存键使用 `code + publishedVersion`，刷新按钮清除对应 code 缓存；
- 选中但已停用/取消发布的 code 保留显示，配置区禁用；
- 加载失败不清空已有 props；
- 校验问题在对应行显示，直到重新选择或删除；
- 分页关闭时隐藏页码和每页条数参数，但保留值，保存前由切换/校验规则判定是否兼容；
- 系统参数单独展示为只读标签。

### Step 5：运行组件、模型和类型测试

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom \
  apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.test.ts \
  apps/web-antd/src/components/form-create/designer/remote-data-source-model.test.ts \
  apps/web-antd/src/components/form-create/helpers.test.ts
pnpm --filter @vben/web-antd run typecheck
```

### Step 6：提交

```bash
git add ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.vue \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.test.ts \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/rules/use-remote-data-source-rule.ts \
  ruoyi-office-vben/apps/web-antd/src/components/form-create/helpers.ts \
  ruoyi-office-vben/apps/web-antd/src/plugins/form-create/index.ts
git commit -m "feat(bpm): add schema-driven data source property editor"
```

## Task 6：补齐当前数据源中文 Schema 并发布新版本

**Files:**

- Runtime data only; do not edit historical rows or checked-in SQL.
- Optional evidence note: `docs/superpowers/verification/2026-07-18-data-source-schema-label-backfill.md`

### Step 1：盘点当前发布数据源

通过现有“表单数据源”管理页和只读数据库查询核对所有启用且有 `published_version` 的数据源，记录：code、当前版本、参数 Schema、结果 Schema。

验收查询必须确认：

- 不直接更新旧版本 JSON；
- 每个数据源将创建一个新草稿；
- 新草稿的所有参数/结果字段都有 1–64 字符中文名称。

### Step 2：通过正常版本流程迁移

对每个当前发布数据源：

1. 打开编辑器；
2. 基于当前发布版本生成/保存新草稿；
3. 补齐所有字段中文名称；
4. 试运行；
5. 发布新版本；
6. 验证旧版本仍在版本历史中。

至少明确处理浏览器验收涉及的：

- `oa_available_seals`；
- `crm_customers`。

### Step 3：只读验证迁移结果

- 每个启用数据源的 `published_version` 指向新版本；
- 当前发布版本 parameter/result Schema 中不存在空 label；
- 历史版本数量增加而非被覆盖；
- 元数据接口不返回 `sourceConfig`。

### Step 4：记录证据

若创建验证记录，只包含数据源 code、版本号、字段中文名称和测试结果，不记录 SQL 或敏感配置。

```bash
git add docs/superpowers/verification/2026-07-18-data-source-schema-label-backfill.md
git commit -m "docs(bpm): record schema label backfill verification"
```

## Task 7：回归测试与真实浏览器验收

**Files:**

- Modify only if a regression is discovered; return to the owning task and add a reproducing test before fixing.

### Step 1：后端定向回归

```bash
mvn -pl yudao-module-bpm/yudao-module-bpm-server -am \
  -Dtest=BpmFormDataSourceServiceTest,BpmFormDataSourceControllerSecurityTest,BpmFormDataSourceExecutionServiceTest,BpmFormLinkageValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Step 2：前端定向回归

```bash
cd ruoyi-office-vben
pnpm exec vitest run --dom \
  apps/web-antd/src/views/bpm/form-data-source/data.test.ts \
  apps/web-antd/src/components/form-create/designer/remote-data-source-model.test.ts \
  apps/web-antd/src/components/form-create/designer/remote-data-source-config-editor.test.ts \
  apps/web-antd/src/components/form-create/helpers.test.ts \
  apps/web-antd/src/components/form-create/data-source/expression.test.ts \
  apps/web-antd/src/components/form-create/data-source/linkage.test.ts \
  apps/web-antd/src/components/form-create/components/remote-data-source-select.test.ts
pnpm --filter @vben/web-antd run typecheck
```

### Step 3：重新构建并启动当前分支服务

- 确认前后端进程均来自当前主工作区和 `codex/oa-platform-production`；
- 后端健康检查通过；
- 前端继续使用 `http://127.0.0.1:5666`；
- 若需要重建 Docker 后端，只停止/替换本项目对应容器，不影响无关服务。

### Step 4：浏览器验收表单 49

打开：

```text
http://127.0.0.1:5666/bpm/manager/form/edit?id=49&type=edit
```

依次验证 `oa_available_seals` 和 `crm_customers`：

1. 参数名下拉显示中文名称、技术字段、类型、必填状态；
2. 系统注入参数只读且不能添加；
3. 绑定表达式只包含当前表单、USER、PROCESS 分组；
4. 结果字段和目标字段均为严格下拉；
5. 显示/值/搜索/分页/依赖/快照字段均不能自由输入；
6. 空状态有具体原因；
7. 切换数据源确认后只清理不兼容配置；
8. 管理数据源打开应用内新页签，原表单仍保留；
9. 浏览器网络响应中看不到 SQL、`sourceConfig` 或平台 API 配置。

### Step 5：运行时回归

在发起流程页面验证已有数据源组件：

- 前置依赖未选择时显示“请先完成前置选择”；
- 数据源为空时显示正常空状态，而不是加载失败；
- 选择结果后联动字段按既有 `outputMappings` 回填；
- 刷新后字段顺序和配置保持稳定。

### Step 6：最终差异审计

```bash
git status --short
git diff --check
git log --oneline -8
```

确认：

- 没有修改 `node_modules`；
- 没有把 SQL/sourceConfig 加入安全元数据响应；
- 没有覆盖用户原有 `.env.development` 修改；
- 自动化测试、环境启动、真实浏览器验收分别报告，不用其中一项代替另一项。
