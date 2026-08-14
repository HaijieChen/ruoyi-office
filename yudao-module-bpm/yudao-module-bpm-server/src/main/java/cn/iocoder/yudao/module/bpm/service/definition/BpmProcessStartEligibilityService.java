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
     * 评估发起资格（目录可见 / 预检 canStart）。
     * <p>
     * 薪税：有 create 权限时 {@code canStart=true}（统一目录可露出）；
     * 通用 {@code createProcessInstance} 仍须走 {@link #validateStartOrThrow} 硬拒绝。
     *
     * @param trustedBusinessStart true 时表示可信业务通道（Finance 领域 API）下的权限评估
     */
    BpmProcessStartEligibility evaluate(String processKey, boolean trustedBusinessStart);

    /**
     * 发起列表是否应隐藏（无业务 create 权限或配置缺失时隐藏，禁止仅灰置）。
     * 薪税有 create 时<strong>不</strong>隐藏（验收：统一目录露出）。
     */
    boolean shouldHideFromStartList(String processKey);

    /**
     * 启动/创建流程前强制校验（通用通道）；失败抛出带友好文案的业务异常。
     * 薪税在通用通道<strong>恒拒绝</strong>（即使目录可见）。
     */
    void validateStartOrThrow(String processKey);

    /**
     * 启动/创建流程前强制校验。
     *
     * @param trustedBusinessStart true 表示来自可信业务通道（Finance 独立入口 API）
     */
    void validateStartOrThrow(String processKey, boolean trustedBusinessStart);

    /**
     * 发布校验：已知嵌入式流程必须已配置非空 requiredStartPermission。
     */
    void validateEmbedStartPermissionConfigured(String processKey);
}
