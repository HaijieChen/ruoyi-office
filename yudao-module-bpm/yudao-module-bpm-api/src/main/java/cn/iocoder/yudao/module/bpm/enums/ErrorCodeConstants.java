package cn.iocoder.yudao.module.bpm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Bpm 错误码枚举类
 * <p>
 * bpm 系统，使用 1-009-000-000 段
 */
public interface ErrorCodeConstants {

    // ==========  通用流程处理 模块 1-009-000-000 ==========

    // ========== OA 流程模块 1-009-001-000 ==========
    ErrorCode OA_LEAVE_NOT_EXISTS = new ErrorCode(1_009_001_001, "请假申请不存在");
    ErrorCode OA_TRIP_NOT_EXISTS = new ErrorCode(1_009_001_002, "出差申请不存在");
    ErrorCode OA_OUTING_NOT_EXISTS = new ErrorCode(1_009_001_003, "外出申请不存在");
    ErrorCode OA_DURATION_INVALID = new ErrorCode(1_009_001_004, "开始结束时间无效或时长小于等于 0");
    ErrorCode OA_TRIP_ACCESS_DENIED = new ErrorCode(1_009_001_005, "无权查看该出差申请");
    ErrorCode OA_OUTING_ACCESS_DENIED = new ErrorCode(1_009_001_006, "无权查看该外出申请");

    // ========== 流程模型 1-009-002-000 ==========
    ErrorCode MODEL_KEY_EXISTS = new ErrorCode(1_009_002_000, "已经存在流程标识为【{}】的流程");
    ErrorCode MODEL_NOT_EXISTS = new ErrorCode(1_009_002_001, "流程模型不存在");
    ErrorCode MODEL_KEY_VALID = new ErrorCode(1_009_002_002, "流程标识格式不正确，需要以字母或下划线开头，后接任意字母、数字、中划线、下划线、句点！");
    ErrorCode MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG = new ErrorCode(1_009_002_003, "部署流程失败，原因：流程表单未配置，请点击【修改流程】按钮进行配置");
    ErrorCode MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG = new ErrorCode(1_009_002_004, "部署流程失败，" +
            "原因：用户任务({})未配置审批人，请点击【流程设计】按钮，选择该它的【任务（审批人）】进行配置");
    ErrorCode MODEL_DEPLOY_FAIL_BPMN_START_EVENT_NOT_EXISTS = new ErrorCode(1_009_002_005, "部署流程失败，原因：BPMN 流程图中，没有开始事件");
    ErrorCode MODEL_DEPLOY_FAIL_BPMN_USER_TASK_NAME_NOT_EXISTS = new ErrorCode(1_009_002_006, "部署流程失败，原因：BPMN 流程图中，用户任务({})的名字不存在");
    ErrorCode MODEL_UPDATE_FAIL_NOT_MANAGER = new ErrorCode(1_009_002_007, "操作流程失败，原因：你不是该流程({})的管理员");
    ErrorCode MODEL_DEPLOY_FAIL_FIRST_USER_TASK_CANDIDATE_STRATEGY_ERROR = new ErrorCode(1_009_002_008, "部署流程失败，原因：首个任务({})的审批人不能是【审批人自选】");
    /** 嵌入式业务表单必须配置 requiredStartPermission（权威元数据），否则禁止发布 */
    ErrorCode MODEL_DEPLOY_FAIL_START_PERMISSION_NOT_CONFIG = new ErrorCode(1_009_002_009,
            "部署流程失败，原因：嵌入式流程【{}】未配置发起权限 requiredStartPermission");

    // ========== 流程定义 1-009-003-000 ==========
    ErrorCode PROCESS_DEFINITION_KEY_NOT_MATCH = new ErrorCode(1_009_003_000, "流程定义的标识期望是({})，当前是({})，请修改 BPMN 流程图");
    ErrorCode PROCESS_DEFINITION_NAME_NOT_MATCH = new ErrorCode(1_009_003_001, "流程定义的名字期望是({})，当前是({})，请修改 BPMN 流程图");
    ErrorCode PROCESS_DEFINITION_NOT_EXISTS = new ErrorCode(1_009_003_002, "流程定义不存在");
    ErrorCode PROCESS_DEFINITION_IS_SUSPENDED = new ErrorCode(1_009_003_003, "流程定义处于挂起状态");

    // ========== 流程实例 1-009-004-000 ==========
    ErrorCode PROCESS_INSTANCE_NOT_EXISTS = new ErrorCode(1_009_004_000, "流程实例不存在");
    ErrorCode PROCESS_INSTANCE_CANCEL_FAIL_NOT_EXISTS = new ErrorCode(1_009_004_001, "流程取消失败，流程不处于运行中");
    ErrorCode PROCESS_INSTANCE_CANCEL_FAIL_NOT_SELF = new ErrorCode(1_009_004_002, "流程取消失败，该流程不是你发起的");
    ErrorCode PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_CONFIG = new ErrorCode(1_009_004_003, "任务({})的候选人未配置");
    ErrorCode PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_EXISTS = new ErrorCode(1_009_004_004, "任务({})的候选人({})不存在");
    ErrorCode PROCESS_INSTANCE_START_USER_CAN_START = new ErrorCode(1_009_004_005, "发起流程失败，你没有权限发起该流程");
    /** 嵌入式流程业务发起权限不足或配置缺失；msg 参数为友好文案 */
    ErrorCode PROCESS_INSTANCE_START_PERMISSION_DENIED = new ErrorCode(1_009_004_011, "{}");
    /** EXP-87 G1：create-by-business 调用方非可验证 Finance 服务身份 */
    ErrorCode PROCESS_INSTANCE_BUSINESS_START_CALLER_FORBIDDEN = new ErrorCode(1_009_004_012,
            "仅允许 Finance 服务经可信业务通道发起，调用方身份未通过校验");
    ErrorCode PROCESS_INSTANCE_CANCEL_FAIL_NOT_ALLOW = new ErrorCode(1_009_004_005, "流程取消失败，该流程不允许取消");
    ErrorCode PROCESS_INSTANCE_CANCEL_FAIL_ACTIVE_TASK_FORBIDDEN = new ErrorCode(1_009_004_009,
            "流程取消失败，当前活动任务已进入禁止取消的节点");
    ErrorCode PROCESS_INSTANCE_HTTP_CALL_ERROR = new ErrorCode(1_009_004_006, "流程 Http 请求调用失败");
    ErrorCode PROCESS_INSTANCE_APPROVE_USER_SELECT_ASSIGNEES_NOT_CONFIG = new ErrorCode(1_009_004_007, "下一个任务({})的审批人未配置");
    ErrorCode PROCESS_INSTANCE_CANCEL_CHILD_FAIL_NOT_ALLOW = new ErrorCode(1_009_004_008, "子流程取消失败，子流程不允许取消");
    /** PAY-R19：付款申请须走领域 cancel，禁止流程详情通用发起人取消（同步落账） */
    ErrorCode PROCESS_INSTANCE_CANCEL_FAIL_USE_PAYMENT_DOMAIN = new ErrorCode(1_009_004_010,
            "付款申请请从「付款申请」台账撤回，不能使用流程通用取消");

    // ========== 流程任务 1-009-005-000 ==========
    ErrorCode TASK_OPERATE_FAIL_ASSIGN_NOT_SELF = new ErrorCode(1_009_005_001, "操作失败，原因：该任务的审批人不是你");
    ErrorCode TASK_NOT_EXISTS = new ErrorCode(1_009_005_002, "流程任务不存在");
    ErrorCode TASK_IS_PENDING = new ErrorCode(1_009_005_003, "当前任务处于挂起状态，不能操作");
    ErrorCode TASK_TARGET_NODE_NOT_EXISTS = new ErrorCode(1_009_005_004, " 目标节点不存在");
    ErrorCode TASK_RETURN_FAIL_SOURCE_TARGET_ERROR = new ErrorCode(1_009_005_006, "退回任务失败，目标节点是在并行网关上或非同一路线上，不可跳转");
    ErrorCode TASK_DELEGATE_FAIL_USER_REPEAT = new ErrorCode(1_009_005_007, "任务委派失败，委派人和当前审批人为同一人");
    ErrorCode TASK_DELEGATE_FAIL_USER_NOT_EXISTS = new ErrorCode(1_009_005_008, "任务委派失败，被委派人不存在");
    ErrorCode TASK_SIGN_CREATE_USER_NOT_EXIST = new ErrorCode(1_009_005_009, "任务加签：选择的用户不存在");
    ErrorCode TASK_SIGN_CREATE_TYPE_ERROR = new ErrorCode(1_009_005_010, "任务加签：当前任务已经{}，不能{}");
    ErrorCode TASK_SIGN_CREATE_USER_REPEAT = new ErrorCode(1_009_005_011, "任务加签失败，加签人与现有审批人[{}]重复");
    ErrorCode TASK_SIGN_DELETE_NO_PARENT = new ErrorCode(1_009_005_012, "任务减签失败，被减签的任务必须是通过加签生成的任务");
    ErrorCode TASK_TRANSFER_FAIL_USER_REPEAT = new ErrorCode(1_009_005_013, "任务转办失败，转办人和当前审批人为同一人");
    ErrorCode TASK_TRANSFER_FAIL_USER_NOT_EXISTS = new ErrorCode(1_009_005_014, "任务转办失败，转办人不存在");
    ErrorCode TASK_SIGNATURE_NOT_EXISTS = new ErrorCode(1_009_005_015, "签名不能为空！");
    ErrorCode TASK_REASON_REQUIRE = new ErrorCode(1_009_005_016, "审批意见不能为空！");
    ErrorCode TASK_WITHDRAW_FAIL_PROCESS_NOT_RUNNING = new ErrorCode(1_009_005_017, "撤回失败，流程实例未运行！");
    ErrorCode TASK_WITHDRAW_FAIL_TASK_NOT_EXISTS = new ErrorCode(1_009_005_018, "撤回失败，未查询到用户已办任务！");
    ErrorCode TASK_WITHDRAW_FAIL_NOT_ALLOW = new ErrorCode(1_009_005_019, "撤回失败，此流程不允许撤回操作！");
    ErrorCode TASK_WITHDRAW_FAIL_NEXT_TASK_NOT_ALLOW = new ErrorCode(1_009_005_020, "撤回失败，下一节点不满足撤回条件！");

    // ========== 动态表单模块 1-009-010-000 ==========
    ErrorCode FORM_NOT_EXISTS = new ErrorCode(1_009_010_000, "动态表单不存在");
    ErrorCode FORM_FIELD_REPEAT = new ErrorCode(1_009_010_001, "表单项({}) 和 ({}) 使用了相同的字段名({})");

    // ========== 动态表单数据源 1-009-010-010 ~ 1-009-010-030 ==========
    ErrorCode BPM_DATA_SOURCE_SQL_INVALID = new ErrorCode(1_009_010_010, "数据源 SQL 不合法");
    ErrorCode BPM_DATA_SOURCE_SQL_READ_ONLY = new ErrorCode(1_009_010_011, "数据源 SQL 仅允许 SELECT 查询");
    ErrorCode BPM_DATA_SOURCE_PARAM_MISSING = new ErrorCode(1_009_010_012, "数据源参数({})缺失");
    ErrorCode BPM_DATA_SOURCE_PARAM_TYPE_MISMATCH = new ErrorCode(1_009_010_013, "数据源参数({})类型不匹配");
    ErrorCode BPM_DATA_SOURCE_PARAM_RESERVED = new ErrorCode(1_009_010_014, "数据源参数({})为系统保留参数，不允许客户端覆盖");
    ErrorCode BPM_DATA_SOURCE_UNPUBLISHED = new ErrorCode(1_009_010_015, "数据源未发布");
    ErrorCode BPM_DATA_SOURCE_TIMEOUT = new ErrorCode(1_009_010_016, "数据源查询超时");
    ErrorCode BPM_DATA_SOURCE_ROW_LIMIT = new ErrorCode(1_009_010_017, "数据源查询结果超出行数限制");
    ErrorCode BPM_DATA_SOURCE_IN_USE = new ErrorCode(1_009_010_018, "数据源正在使用中，不允许删除");
    ErrorCode BPM_DATA_SOURCE_DEPENDENCY_CYCLE = new ErrorCode(1_009_010_019, "数据源存在循环依赖");
    ErrorCode BPM_DATA_SOURCE_RESULT_MAPPING_MISMATCH = new ErrorCode(1_009_010_020, "数据源查询结果与字段映射不匹配");
    ErrorCode BPM_DATA_SOURCE_CONFIG_INVALID = new ErrorCode(1_009_010_021, "数据源配置不合法");
    ErrorCode BPM_DATA_SOURCE_EXECUTION_FAILED = new ErrorCode(1_009_010_022, "数据源执行失败");
    ErrorCode BPM_DATA_SOURCE_NOT_EXISTS = new ErrorCode(1_009_010_023, "数据源不存在");
    ErrorCode BPM_DATA_SOURCE_CODE_DUPLICATE = new ErrorCode(1_009_010_024, "数据源标识({})已存在");
    ErrorCode BPM_DATA_SOURCE_VERSION_NOT_EXISTS = new ErrorCode(1_009_010_025, "数据源版本不存在");
    ErrorCode BPM_DATA_SOURCE_VERSION_STATE_INVALID = new ErrorCode(1_009_010_026, "数据源版本状态不允许当前操作");
    ErrorCode BPM_DATA_SOURCE_FORM_NOT_REFERENCED = new ErrorCode(1_009_010_027, "表单未引用该数据源");
    ErrorCode BPM_DATA_SOURCE_VERSION_CONFLICT = new ErrorCode(1_009_010_028, "数据源版本并发冲突，请刷新后重试");
    ErrorCode BPM_DATA_SOURCE_FORM_ACCESS_DENIED = new ErrorCode(1_009_010_029, "无权通过当前流程上下文访问表单数据源");
    ErrorCode BPM_DATA_SOURCE_PARAM_INVALID = new ErrorCode(1_009_010_030, "数据源请求参数不合法或超出大小限制");

    // ========== 用户组模块 1-009-011-000 ==========
    ErrorCode USER_GROUP_NOT_EXISTS = new ErrorCode(1_009_011_000, "用户分组不存在");
    ErrorCode USER_GROUP_IS_DISABLE = new ErrorCode(1_009_011_001, "名字为【{}】的用户分组已被禁用");

    // ========== 用户组模块 1-009-012-000 ==========
    ErrorCode CATEGORY_NOT_EXISTS = new ErrorCode(1_009_012_000, "流程分类不存在");
    ErrorCode CATEGORY_NAME_DUPLICATE = new ErrorCode(1_009_012_001, "流程分类名字【{}】重复");
    ErrorCode CATEGORY_CODE_DUPLICATE = new ErrorCode(1_009_012_002, "流程分类编码【{}】重复");
    ErrorCode CATEGORY_DELETE_FAIL_MODEL_USED = new ErrorCode(1_009_012_003, "删除失败，流程分类【{}】已被流程模型使用，请先删除对应的流程模型");

    // ========== BPM 流程监听器 1-009-013-000 ==========
    ErrorCode PROCESS_LISTENER_NOT_EXISTS = new ErrorCode(1_009_013_000, "流程监听器不存在");
    ErrorCode PROCESS_LISTENER_CLASS_NOT_FOUND = new ErrorCode(1_009_013_001, "流程监听器类({})不存在");
    ErrorCode PROCESS_LISTENER_CLASS_IMPLEMENTS_ERROR = new ErrorCode(1_009_013_002, "流程监听器类({})没有实现接口({})");
    ErrorCode PROCESS_LISTENER_EXPRESSION_INVALID = new ErrorCode(1_009_013_003, "流程监听器表达式({})不合法");

    // ========== BPM 流程表达式 1-009-014-000 ==========
    ErrorCode PROCESS_EXPRESSION_NOT_EXISTS = new ErrorCode(1_009_014_000, "流程表达式不存在");

}
