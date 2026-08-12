package cn.iocoder.yudao.module.bpm.service.definition;

/**
 * 流程发起资格评估（嵌入式业务权限 + 配置完整性）。
 */
public interface BpmProcessStartEligibilityService {

    /**
     * 评估当前登录用户对 processKey 的发起资格。
     */
    BpmProcessStartEligibility evaluate(String processKey);

    /**
     * 发起列表是否应隐藏（无业务 create 权限或配置缺失时隐藏，禁止仅灰置）。
     */
    boolean shouldHideFromStartList(String processKey);

    /**
     * 启动/创建流程前强制校验；失败抛出带友好文案的业务异常。
     */
    void validateStartOrThrow(String processKey);

    /**
     * 发布校验：已知嵌入式流程必须已配置非空 requiredStartPermission。
     */
    void validateEmbedStartPermissionConfigured(String processKey);
}
