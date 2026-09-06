package cn.iocoder.yudao.module.bpm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** Errors for initiator withdrawal (not process cancellation or approver withdrawal). */
public interface BpmInitiatorWithdrawErrorCodeConstants {
    ErrorCode INITIATOR_WITHDRAW_DISABLED = new ErrorCode(1_009_004_030, "发起人撤回失败，该流程不允许撤回");
    ErrorCode INITIATOR_WITHDRAW_HUMAN_RESULT = new ErrorCode(1_009_004_031, "发起人撤回失败，流程已经产生人工审批结果");
    ErrorCode INITIATOR_WITHDRAW_CONCURRENT_CHANGE = new ErrorCode(1_009_004_032, "流程状态已并发变更，请刷新后重试");
}
