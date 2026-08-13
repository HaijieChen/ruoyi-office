package cn.iocoder.yudao.module.bpm.service.definition;

/**
 * 流程发起资格评估（嵌入式业务权限 + 配置完整性）。
 */
public interface BpmProcessStartEligibilityService {

    /**
     * 评估当前登录用户对 processKey 的发起资格（通用通道）。
     */
    BpmProcessStartEligibility evaluate(String processKey);

    /**
     * 评估发起资格。
     *
     * @param trustedBusinessStart true 时允许菜单专属流程（薪资/税金）走业务权限校验，而非通用通道恒 deny
     */
    BpmProcessStartEligibility evaluate(String processKey, boolean trustedBusinessStart);

    /**
     * 发起列表是否应隐藏（无业务 create 权限或配置缺失时隐藏，禁止仅灰置）。
     */
    boolean shouldHideFromStartList(String processKey);

    /**
     * 启动/创建流程前强制校验（通用通道）；失败抛出带友好文案的业务异常。
     */
    void validateStartOrThrow(String processKey);

    /**
     * 启动/创建流程前强制校验。
     *
     * @param trustedBusinessStart true 表示来自可信业务通道（Finance 独立入口）
     */
    void validateStartOrThrow(String processKey, boolean trustedBusinessStart);

    /**
     * 发布校验：已知嵌入式流程必须已配置非空 requiredStartPermission。
     */
    void validateEmbedStartPermissionConfigured(String processKey);
}
