package cn.iocoder.yudao.module.finance.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    // ========== 银行到款 1-040-000-000 ==========
    ErrorCode RECEIPT_NOT_EXISTS = new ErrorCode(1_040_000_000, "银行到款记录不存在");
    ErrorCode RECEIPT_NO_EXISTS = new ErrorCode(1_040_000_001, "到款流水号已存在");
    ErrorCode RECEIPT_BANK_SERIAL_NO_EXISTS = new ErrorCode(1_040_000_002, "银行流水号已存在");
    ErrorCode RECEIPT_CLOSE_REASON_REQUIRED = new ErrorCode(1_040_000_003, "关闭原因不能为空");
    ErrorCode RECEIPT_REOPEN_REASON_REQUIRED = new ErrorCode(1_040_000_004, "重开原因不能为空");
    ErrorCode RECEIPT_CLOSE_STATUS_INVALID = new ErrorCode(1_040_000_005,
            "仅可关闭存在未认领金额的待认领或部分认领银行到款");
    ErrorCode RECEIPT_REOPEN_STATUS_INVALID = new ErrorCode(1_040_000_006, "仅可重开已关闭的银行到款");
    ErrorCode RECEIPT_CONCURRENT_MODIFICATION = new ErrorCode(1_040_000_007,
            "银行到款状态已变化，请刷新后重试");
    ErrorCode RECEIPT_UPDATE_STATUS_INVALID = new ErrorCode(1_040_000_008,
            "仅未认领且无认领金额的银行到款可修改");
    ErrorCode RECEIPT_DELETE_STATUS_INVALID = new ErrorCode(1_040_000_009,
            "仅未认领且无认领金额的银行到款可删除");
    /** 业务款写路径：付款方必填（非业务款可空） */
    ErrorCode RECEIPT_PAYER_NAME_REQUIRED_FOR_BUSINESS_FUND = new ErrorCode(1_040_000_013,
            "业务款时付款方名称不能为空");
    ErrorCode ENTITY_COMPANY_REQUIRED = new ErrorCode(1_040_000_010, "主体公司不能为空");
    ErrorCode ENTITY_COMPANY_INVALID = new ErrorCode(1_040_000_011, "主体公司不存在或未启用");
    ErrorCode ENTITY_COMPANY_NAME_DUPLICATE = new ErrorCode(1_040_000_012,
            "主体公司名称重复，请改用唯一名称");

    // ========== 商务单 1-040-001-000 ==========
    ErrorCode BUSINESS_ORDER_NOT_EXISTS = new ErrorCode(1_040_001_000, "商务单不存在");
    ErrorCode BUSINESS_ORDER_NO_EXISTS = new ErrorCode(1_040_001_001, "商务单号已存在");
    ErrorCode BUSINESS_ORDER_AMOUNT_INVALID = new ErrorCode(1_040_001_003, "应收或应付金额至少有一项大于 0，且金额不能为负数");
    ErrorCode BUSINESS_ORDER_CLOSED = new ErrorCode(1_040_001_004, "已关闭的商务单不能修改");
    ErrorCode BUSINESS_ORDER_DELETE_ONLY_DRAFT = new ErrorCode(1_040_001_005, "只能删除草稿状态的商务单");
    ErrorCode BUSINESS_ORDER_STATUS_INVALID = new ErrorCode(1_040_001_006, "商务单状态无效");
    ErrorCode BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED = new ErrorCode(1_040_001_007,
            "应收金额不能低于已确认认领金额");
    ErrorCode BUSINESS_ORDER_DELETE_HAS_CLAIM = new ErrorCode(1_040_001_008,
            "已有确认认领金额的商务单不能删除");
    ErrorCode BUSINESS_ORDER_CONTRACT_REQUIRED = new ErrorCode(1_040_001_009,
            "商务单必须关联已通过的合同签约申请");
    ErrorCode BUSINESS_ORDER_CONTRACT_INVALID = new ErrorCode(1_040_001_010,
            "合同不存在、未通过或不属于当前用户，无法关联");
    ErrorCode BUSINESS_ORDER_CONTRACT_CLEAR_FORBIDDEN = new ErrorCode(1_040_001_011,
            "禁止清空商务单已关联合同");
    ErrorCode BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN = new ErrorCode(1_040_001_012,
            "存在开票占用时禁止更换合同");
    /** 合同产品类型为空，无法作为商务单产品权威源 */
    ErrorCode BUSINESS_ORDER_CONTRACT_PRODUCT_MISSING = new ErrorCode(1_040_001_013,
            "合同产品类型为空，无法关联到商务单");
    /** 商务单存在 PENDING/APPROVED 有效开票明细时禁止换合同 */
    ErrorCode BUSINESS_ORDER_ACTIVE_INVOICE_BLOCKS_CONTRACT_CHANGE = new ErrorCode(1_040_001_014,
            "商务单存在审批中或已通过的开票明细，禁止更换合同");

    // ========== 到款认领 1-040-002-000 ==========
    ErrorCode RECEIPT_CLAIM_NOT_EXISTS = new ErrorCode(1_040_002_000, "到款认领单不存在");
    ErrorCode RECEIPT_CLAIM_STATUS_INVALID = new ErrorCode(1_040_002_001, "当前状态不允许执行该操作");
    ErrorCode RECEIPT_CLAIM_NOT_OWNER = new ErrorCode(1_040_002_002, "只能操作本人提交的到款认领单");
    ErrorCode RECEIPT_CLAIM_ITEMS_EMPTY = new ErrorCode(1_040_002_003, "认领明细不能为空");
    ErrorCode RECEIPT_CLAIM_AMOUNT_INVALID = new ErrorCode(1_040_002_004, "认领金额必须大于 0");
    ErrorCode RECEIPT_CLAIM_ITEM_DUPLICATE = new ErrorCode(1_040_002_005, "同一到款和商务单不能重复分摊");
    ErrorCode RECEIPT_CLAIM_RECEIPT_INVALID = new ErrorCode(1_040_002_006, "银行到款不存在或可认领金额不足");
    ErrorCode RECEIPT_CLAIM_BUSINESS_ORDER_INVALID = new ErrorCode(1_040_002_007,
            "商务单不存在、非有效状态、非本人负责或待回款金额不足");
    ErrorCode RECEIPT_CLAIM_REJECT_REASON_REQUIRED = new ErrorCode(1_040_002_008, "驳回原因不能为空");
    ErrorCode RECEIPT_CLAIM_CONCURRENT_MODIFICATION = new ErrorCode(1_040_002_009,
            "认领单已处理或余额已变化，请刷新后重试");
    ErrorCode RECEIPT_CLAIM_REVOKE_REASON_REQUIRED = new ErrorCode(1_040_002_010, "撤销原因不能为空");
    ErrorCode RECEIPT_CLAIM_RECEIPT_CLOSED = new ErrorCode(1_040_002_011, "已关闭的银行到款不能认领");
    ErrorCode RECEIPT_CLAIM_LEGACY_WRITE_FORBIDDEN = new ErrorCode(1_040_002_012,
            "历史商务单认领禁止一切写操作（含撤销）");
    ErrorCode RECEIPT_CLAIM_INVOICE_INVALID = new ErrorCode(1_040_002_013,
            "开票申请不存在、不可认领、无权限或可认领金额不足");
    ErrorCode RECEIPT_CLAIM_SOURCE_INVALID = new ErrorCode(1_040_002_014,
            "认领明细必须挂开票申请（新链路），且来源一致");
    ErrorCode RECEIPT_CLAIM_ITEM_DUPLICATE_INVOICE = new ErrorCode(1_040_002_015,
            "同一到款和开票申请不能重复分摊");
    ErrorCode RECEIPT_CLAIM_RECEIPT_NOT_BUSINESS_FUND = new ErrorCode(1_040_002_016,
            "仅业务款可发起到款认领");

    // ========== 开票申请 1-040-003-000 ==========
    ErrorCode INVOICE_APPLICATION_NOT_EXISTS = new ErrorCode(1_040_003_000, "开票申请不存在");
    ErrorCode INVOICE_APPLICATION_LINES_EMPTY = new ErrorCode(1_040_003_001, "开票申请明细不能为空");
    ErrorCode INVOICE_APPLICATION_AMOUNT_INVALID = new ErrorCode(1_040_003_002, "开票金额必须大于 0");
    ErrorCode INVOICE_APPLICATION_OCCUPY_EXCEED = new ErrorCode(1_040_003_003,
            "商务单可开票金额不足，无法占用");
    ErrorCode INVOICE_APPLICATION_OCCUPY_CONCURRENT = new ErrorCode(1_040_003_004,
            "商务单开票占用已变化，请刷新后重试");
    ErrorCode INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS = new ErrorCode(1_040_003_005,
            "开票明细关联的商务单不存在");
    ErrorCode INVOICE_APPLICATION_STATUS_INVALID = new ErrorCode(1_040_003_006,
            "当前审批状态不允许执行该操作");
    ErrorCode INVOICE_APPLICATION_APPROVAL_OUTCOME_INVALID = new ErrorCode(1_040_003_007,
            "审批结果非法或状态迁移不被允许");
    ErrorCode INVOICE_APPLICATION_RELEASE_OCCUPY_FAILED = new ErrorCode(1_040_003_008,
            "释放商务单开票占用失败，请刷新后重试");
    ErrorCode INVOICE_APPLICATION_LINE_NOT_EXISTS = new ErrorCode(1_040_003_009, "开票申请明细不存在");
    ErrorCode INVOICE_APPLICATION_ISSUE_NOT_ALLOWED = new ErrorCode(1_040_003_010,
            "仅审批通过且未作废的开票申请可办票");
    ErrorCode INVOICE_APPLICATION_LINE_ALREADY_ISSUED = new ErrorCode(1_040_003_011,
            "该明细已开票，一期一行仅允许一张物理票");
    ErrorCode INVOICE_APPLICATION_CUSTOMER_COMPANY_REQUIRED = new ErrorCode(1_040_003_012,
            "必须选择启用中的客户公司");
    ErrorCode INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED = new ErrorCode(1_040_003_013,
            "客户公司已停用，请重新选择");
    ErrorCode INVOICE_APPLICATION_COMPLETE_ISSUE_FILES_EMPTY = new ErrorCode(1_040_003_014,
            "整单办票至少需要一个发票附件");
    ErrorCode INVOICE_APPLICATION_USE_COMPLETE_ISSUE = new ErrorCode(1_040_003_015,
            "请使用整单办票 complete-issue，不再支持一行一票办票");
    ErrorCode INVOICE_APPLICATION_PRODUCT_TYPE_INVALID = new ErrorCode(1_040_003_016,
            "产品类型不在启用字典中");
    /** 同一开票申请内商务单产品类型不一致 */
    ErrorCode INVOICE_APPLICATION_PRODUCT_MIXED = new ErrorCode(1_040_003_017,
            "同一开票申请内全部商务单必须属于同一产品类型");
    /** 商务单产品未固化（快照与 legacy 名称均为空） */
    ErrorCode INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING = new ErrorCode(1_040_003_018,
            "商务单产品类型未固化，无法开票");

    // ========== 客户公司 / 客商 1-040-004-000 ==========
    ErrorCode CUSTOMER_COMPANY_NOT_EXISTS = new ErrorCode(1_040_004_000, "客户公司不存在");
    ErrorCode CUSTOMER_COMPANY_TAX_NO_EXISTS = new ErrorCode(1_040_004_001, "纳税人识别号已存在");
    ErrorCode CUSTOMER_COMPANY_NAME_REQUIRED = new ErrorCode(1_040_004_002, "客户公司名称不能为空");
    ErrorCode CUSTOMER_COMPANY_TAX_NO_REQUIRED = new ErrorCode(1_040_004_003, "纳税人识别号不能为空");
    ErrorCode CUSTOMER_COMPANY_STATUS_INVALID = new ErrorCode(1_040_004_004, "客户公司状态无效");
    ErrorCode CUSTOMER_COMPANY_ROLE_REQUIRED = new ErrorCode(1_040_004_005, "至少选择客户或供应商角色之一");
    ErrorCode CUSTOMER_COMPANY_SUPPLIER_BANK_REQUIRED = new ErrorCode(1_040_004_006,
            "供应商角色启用时开户银行与银行账号不能为空");
    ErrorCode CUSTOMER_COMPANY_NOT_CUSTOMER_ROLE = new ErrorCode(1_040_004_007,
            "所选客商不含客户角色，不能作为开票购方或合同对方");
    ErrorCode CUSTOMER_COMPANY_NOT_SUPPLIER_ROLE = new ErrorCode(1_040_004_008,
            "所选客商不含供应商角色或银行信息不齐全，不能作为付款收款方");

    // ========== 合同签约申请 1-040-005-000 ==========
    ErrorCode CONTRACT_APPLICATION_NOT_EXISTS = new ErrorCode(1_040_005_000, "合同签约申请不存在");
    ErrorCode CONTRACT_APPLICATION_STATUS_INVALID = new ErrorCode(1_040_005_001,
            "当前审批状态不允许执行该操作");
    ErrorCode CONTRACT_APPLICATION_APPROVAL_OUTCOME_INVALID = new ErrorCode(1_040_005_002,
            "审批结果非法或状态迁移不被允许");
    ErrorCode CONTRACT_APPLICATION_COUNTERPARTY_REQUIRED = new ErrorCode(1_040_005_003,
            "必须选择启用中的对方（客商）公司");
    ErrorCode CONTRACT_APPLICATION_AMOUNT_INVALID = new ErrorCode(1_040_005_004,
            "合同金额无效：未勾选金额不适用时金额必须大于 0");
    ErrorCode CONTRACT_APPLICATION_FIELD_REQUIRED = new ErrorCode(1_040_005_005,
            "合同签约申请必填字段不完整");
    ErrorCode CONTRACT_APPLICATION_CANCEL_NOT_ALLOWED = new ErrorCode(1_040_005_006,
            "已进入用印及之后节点，禁止申请人撤回");
    ErrorCode CONTRACT_APPLICATION_PRE_PROCESS_REQUIRED = new ErrorCode(1_040_005_007,
            "采购合同/租赁合同必须填写前置流程");
    ErrorCode CONTRACT_APPLICATION_MAIL_ADDRESS_REQUIRED = new ErrorCode(1_040_005_008,
            "需要邮寄时必须填写邮寄地址");
    ErrorCode CONTRACT_APPLICATION_FILE_TYPE_INVALID = new ErrorCode(1_040_005_009,
            "文件类型不在允许枚举内");
    ErrorCode CONTRACT_APPLICATION_SEAL_FILE_REQUIRED = new ErrorCode(1_040_005_010,
            "用印备案扫描件不能为空");
    ErrorCode CONTRACT_APPLICATION_MAIL_TRACKING_REQUIRED = new ErrorCode(1_040_005_011,
            "邮寄单号不能为空");
    ErrorCode CONTRACT_APPLICATION_EXEC_NOT_ALLOWED = new ErrorCode(1_040_005_012,
            "当前状态或节点不允许执行该用印/归档/邮寄操作");
    ErrorCode CONTRACT_APPLICATION_APPROVED_EVIDENCE_INCOMPLETE = new ErrorCode(1_040_005_013,
            "合同签约未完成用印/归档/邮寄证据，不能标记为已通过");
    ErrorCode CONTRACT_APPLICATION_ACCESS_DENIED = new ErrorCode(1_040_005_014,
            "无权查看或操作该合同签约申请");
    ErrorCode CONTRACT_APPLICATION_TASK_INVALID = new ErrorCode(1_040_005_015,
            "BPM 任务无效、节点不匹配或当前用户无权执行");
    ErrorCode CONTRACT_APPLICATION_PRODUCT_TYPE_INVALID = new ErrorCode(1_040_005_016,
            "产品类型不在启用字典中");

    // ========== 付款申请 / 前置引用 1-040-006-000 ==========
    ErrorCode PAYMENT_PURCHASE_REF_INVALID = new ErrorCode(1_040_006_000,
            "采购前置无效：须为本人发起且已通过的白名单采购流程实例");
    ErrorCode PAYMENT_LEASE_REF_INVALID = new ErrorCode(1_040_006_001,
            "租赁前置无效：须为本人发起且已通过的租赁合同签约申请");
    ErrorCode PAYMENT_BPM_HISTORY_UNAVAILABLE = new ErrorCode(1_040_006_002,
            "BPM 历史服务不可用，无法校验或列出采购流程实例");
    ErrorCode PAYMENT_APPLICATION_NOT_EXISTS = new ErrorCode(1_040_006_003, "付款申请不存在");
    ErrorCode PAYMENT_APPLICATION_STATUS_INVALID = new ErrorCode(1_040_006_004,
            "当前状态不允许执行该操作");
    ErrorCode PAYMENT_APPLICATION_ACCESS_DENIED = new ErrorCode(1_040_006_005,
            "无权查看或操作该付款申请");
    ErrorCode PAYMENT_APPLICATION_AMOUNT_INVALID = new ErrorCode(1_040_006_006,
            "申请付款金额必须大于 0");
    ErrorCode PAYMENT_APPLICATION_FIELD_REQUIRED = new ErrorCode(1_040_006_007,
            "付款申请必填字段不完整");
    ErrorCode PAYMENT_APPLICATION_EVIDENCE_REQUIRED = new ErrorCode(1_040_006_008,
            "付款依据附件至少一份");
    ErrorCode PAYMENT_APPLICATION_CASHIER_FIELDS_REQUIRED = new ErrorCode(1_040_006_009,
            "出纳办结须填写实际支付日期与支付凭证");
    ErrorCode PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID = new ErrorCode(1_040_006_010,
            "审批结果非法或状态迁移不被允许");
    ErrorCode PAYMENT_APPLICATION_TASK_INVALID = new ErrorCode(1_040_006_011,
            "BPM 任务无效、节点不匹配或当前用户无权执行");
    ErrorCode PAYMENT_APPLICATION_REASON_INVALID = new ErrorCode(1_040_006_012, "付款事由无效");
    ErrorCode PAYMENT_APPLICATION_TIMING_INVALID = new ErrorCode(1_040_006_013, "支付时效无效");
    ErrorCode PAYMENT_APPLICATION_ACCOUNTING_SUBJECT_REQUIRED = new ErrorCode(1_040_006_014,
            "财务主管节点须填写费用科目/性质");
    ErrorCode PAYMENT_RELATED_CONTRACT_INVALID = new ErrorCode(1_040_006_015,
            "关联合同无效：须为已通过且未作废的合同签约申请");
    ErrorCode PAYMENT_RELATED_CONTRACT_REASON_INVALID = new ErrorCode(1_040_006_016,
            "仅业务付款允许关联合同；租赁请使用租赁前置合同");
    ErrorCode PAYMENT_APPLICATION_DICT_INVALID = new ErrorCode(1_040_006_017,
            "费用项目、支付方式或费用科目/性质不在启用字典中");
    ErrorCode PAYMENT_APPLICATION_DEPT_REQUIRED = new ErrorCode(1_040_006_018,
            "申请人档案缺少部门，无法发起付款");
    ErrorCode PAYMENT_APPLICATION_EVIDENCE_URL_INVALID = new ErrorCode(1_040_006_019,
            "付款依据或支付凭证须为可识别的文件 URL");
    /** 交易币种仅允许 CNY/USD/HKD */
    ErrorCode PAYMENT_APPLICATION_CURRENCY_INVALID = new ErrorCode(1_040_006_020,
            "币种仅支持 CNY/USD/HKD");
    /** 出纳支付须选择付款账户 */
    ErrorCode PAYMENT_APPLICATION_PAY_ACCOUNT_REQUIRED = new ErrorCode(1_040_006_021,
            "出纳支付须选择付款账户");
    /** 支付金额无效或超过未付余额 */
    ErrorCode PAYMENT_APPLICATION_PAY_AMOUNT_INVALID = new ErrorCode(1_040_006_022,
            "支付金额必须大于 0，且累计支付不得超过申请金额");
    /** 支付合计未达申请金额，禁止办结 */
    ErrorCode PAYMENT_APPLICATION_PAY_AMOUNT_INCOMPLETE = new ErrorCode(1_040_006_023,
            "支付合计须等于申请金额后方可办结");
    /** 普通付款新单禁止薪资/税金事由 */
    ErrorCode PAYMENT_APPLICATION_REASON_SALARY_TAX_FORBIDDEN = new ErrorCode(1_040_006_024,
            "普通付款不可新建薪资/税金事由，请使用独立薪资或税金付款入口");
    /** 薪资/税金明细为空或校验失败 */
    ErrorCode PAYMENT_APPLICATION_LINES_INVALID = new ErrorCode(1_040_006_025,
            "薪资/税金明细不完整或金额校验失败");
    /** 申请业务类型与操作不匹配 */
    ErrorCode PAYMENT_APPLICATION_KIND_INVALID = new ErrorCode(1_040_006_026,
            "付款申请业务类型不匹配");
    /** V1 一单一币种 */
    ErrorCode PAYMENT_APPLICATION_MULTI_CURRENCY_FORBIDDEN = new ErrorCode(1_040_006_027,
            "同一申请仅允许一个币种，跨币种请分单");
    /** 付款账户币种与申请币种不一致 */
    ErrorCode PAYMENT_ACCOUNT_CURRENCY_MISMATCH = new ErrorCode(1_040_006_028,
            "付款账户币种与申请币种不一致，V1 不支持跨币种支付");
    /** 已有实际支付流水，禁止驳回/重提（支付行不可变） */
    ErrorCode PAYMENT_APPLICATION_HAS_PAY_LINES = new ErrorCode(1_040_006_029,
            "已有实际支付记录，禁止驳回或重提；支付流水不可变，请走作废/冲正流程");
    /** 支付幂等键必填 */
    ErrorCode PAYMENT_APPLICATION_IDEMPOTENCY_KEY_REQUIRED = new ErrorCode(1_040_006_030,
            "支付幂等键不能为空");
    /** 同幂等键但载荷指纹不一致 */
    ErrorCode PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT = new ErrorCode(1_040_006_031,
            "支付幂等键已存在但请求载荷不一致，禁止静默覆盖");

    // ========== 公司银行账户 1-040-007-000 ==========
    ErrorCode COMPANY_BANK_ACCOUNT_NOT_EXISTS = new ErrorCode(1_040_007_000, "公司银行账户不存在");
    ErrorCode COMPANY_BANK_ACCOUNT_REQUIRED = new ErrorCode(1_040_007_001, "公司银行账户不能为空");
    ErrorCode COMPANY_BANK_ACCOUNT_DISABLED = new ErrorCode(1_040_007_002, "公司银行账户已停用，不可用于支付");
    ErrorCode COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH = new ErrorCode(1_040_007_003,
            "付款账户不属于该主体公司");
    ErrorCode COMPANY_BANK_ACCOUNT_NO_DUPLICATE = new ErrorCode(1_040_007_004,
            "同一主体公司下银行账号已存在");
    ErrorCode COMPANY_BANK_ACCOUNT_STATUS_INVALID = new ErrorCode(1_040_007_005, "公司银行账户状态无效");
    ErrorCode COMPANY_BANK_ACCOUNT_FIELD_REQUIRED = new ErrorCode(1_040_007_006, "公司银行账户必填字段不完整");

    // ========== 费用报销 1-040-008-000 ==========
    ErrorCode EXPENSE_REIMBURSEMENT_NOT_EXISTS = new ErrorCode(1_040_008_000, "费用报销申请不存在");
    ErrorCode EXPENSE_REIMBURSEMENT_STATUS_INVALID = new ErrorCode(1_040_008_001,
            "当前状态不允许执行该操作");
    ErrorCode EXPENSE_REIMBURSEMENT_ACCESS_DENIED = new ErrorCode(1_040_008_002,
            "无权查看或操作该费用报销申请");
    ErrorCode EXPENSE_REIMBURSEMENT_FIELD_REQUIRED = new ErrorCode(1_040_008_003,
            "费用报销必填字段不完整");
    ErrorCode EXPENSE_REIMBURSEMENT_PERIOD_INVALID = new ErrorCode(1_040_008_004,
            "费用归属期间须为 YYYY-MM");
    ErrorCode EXPENSE_REIMBURSEMENT_AMOUNT_INVALID = new ErrorCode(1_040_008_005,
            "报销金额必须大于 0");
    ErrorCode EXPENSE_REIMBURSEMENT_LINES_EMPTY = new ErrorCode(1_040_008_006,
            "报销明细至少一行");
    ErrorCode EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH = new ErrorCode(1_040_008_007,
            "代票单只能提交代票明细，普通单只能提交普通明细");
    ErrorCode EXPENSE_REIMBURSEMENT_CATEGORY_INVALID = new ErrorCode(1_040_008_008,
            "事由分类不在启用字典中");
    ErrorCode EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID = new ErrorCode(1_040_008_009,
            "实报金额必须大于 0 且不能超过申请总额");
    ErrorCode EXPENSE_REIMBURSEMENT_TASK_INVALID = new ErrorCode(1_040_008_010,
            "BPM 任务无效、节点不匹配或当前用户无权执行");
    ErrorCode EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED = new ErrorCode(1_040_008_011,
            "出纳支付须选择公司银行账户");
    ErrorCode EXPENSE_REIMBURSEMENT_CASHIER_FIELDS_REQUIRED = new ErrorCode(1_040_008_012,
            "出纳办结须填写实际支付日期与支付凭证");
    ErrorCode EXPENSE_REIMBURSEMENT_DEPT_REQUIRED = new ErrorCode(1_040_008_013,
            "申请人档案缺少部门，无法发起报销");
    ErrorCode EXPENSE_REIMBURSEMENT_ATTACHMENT_URL_INVALID = new ErrorCode(1_040_008_014,
            "报销附件或支付凭证须为可识别的文件 URL");
    ErrorCode EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED = new ErrorCode(1_040_008_015,
            "有票普通明细每行必须上传一张发票");
    ErrorCode EXPENSE_REIMBURSEMENT_INVOICE_FORBIDDEN = new ErrorCode(1_040_008_016,
            "无票或代票明细不得上传发票");
    ErrorCode EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED = new ErrorCode(1_040_008_017,
            "差旅必须关联已通过的出差单，交通必须关联已通过的出差或外出单");
    ErrorCode EXPENSE_REIMBURSEMENT_PREDOC_INVALID = new ErrorCode(1_040_008_018,
            "前置出差/外出单无效：须为本人发起且已通过");
    ErrorCode EXPENSE_REIMBURSEMENT_PREDOC_FORBIDDEN = new ErrorCode(1_040_008_019,
            "该事由分类不得关联出差或外出单");
    ErrorCode EXPENSE_REIMBURSEMENT_INVOICE_MODE_MISMATCH = new ErrorCode(1_040_008_020,
            "发票模式与发起入口不一致");
    ErrorCode EXPENSE_REIMBURSEMENT_STAY_TIER_REQUIRED = new ErrorCode(1_040_008_021,
            "差旅须关联带城市的出差或外出单以裁定住宿标准");
    ErrorCode EXPENSE_REIMBURSEMENT_OVER_LIMIT_REASON_REQUIRED = new ErrorCode(1_040_008_022,
            "差旅住宿超标须填写超标原因");
    ErrorCode EXPENSE_REIMBURSEMENT_INVOICE_USED = new ErrorCode(1_040_008_023,
            "发票已被使用");

    // ========== EXP-73 通用币种契约（P1/P2 共用） ==========
    /** 交易币种仅允许 CNY/USD/HKD */
    ErrorCode CURRENCY_INVALID = new ErrorCode(1_040_000_030, "币种仅支持 CNY/USD/HKD");
    /** 关联单据币种必须一致（本期不做折算） */
    ErrorCode CURRENCY_MISMATCH = new ErrorCode(1_040_000_031,
            "关联单据币种不一致，本期不支持跨币种折算");
    ErrorCode EXCHANGE_RATE_MISSING = new ErrorCode(1_040_000_032,
            "缺少当月汇率，请先在汇率维护中录入");

    // ========== 部门费用分摊 1-040-008-000 ==========
    ErrorCode DEPT_ALLOCATION_IMPORT_EMPTY = new ErrorCode(1_040_009_000, "分摊导入数据不能为空");
    ErrorCode DEPT_ALLOCATION_SOURCE_TYPE_INVALID = new ErrorCode(1_040_009_001,
            "来源类型必须为薪资、云服务或其他");
    ErrorCode DEPT_ALLOCATION_IMPORT_INVALID = new ErrorCode(1_040_009_002, "分摊导入校验失败：{}");

    // ========== 开票红冲 1-040-010-000 ==========
    ErrorCode INVOICE_REDFUSH_REASON_REQUIRED = new ErrorCode(1_040_010_000, "红冲原因不能为空");
    ErrorCode INVOICE_REDFUSH_PREDECESSOR_INVALID = new ErrorCode(1_040_010_001,
            "只能红冲已办完票、无认领、未锁定的开票申请");
    ErrorCode INVOICE_REDFUSH_AMOUNT_MISMATCH = new ErrorCode(1_040_010_002,
            "红冲金额必须与原开票申请一致");
    ErrorCode INVOICE_REDFUSH_LOCK_FAILED = new ErrorCode(1_040_010_003,
            "原开票申请已被锁定或不可红冲，请刷新后重试");
    ErrorCode INVOICE_REDFUSH_NOT_EXISTS = new ErrorCode(1_040_010_004, "红冲申请不存在");
    ErrorCode INVOICE_REDFUSH_STATUS_INVALID = new ErrorCode(1_040_010_005,
            "当前审批状态不允许执行该操作");

    // ========== 公司财务审批人 1-040-011-000 ==========
    ErrorCode COMPANY_APPROVER_USER_REQUIRED = new ErrorCode(1_040_011_000, "请至少选择一名财务审批人");
    ErrorCode COMPANY_APPROVER_USER_INVALID = new ErrorCode(1_040_011_001, "财务审批人不存在或已停用");

}
