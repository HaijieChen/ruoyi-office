package cn.iocoder.yudao.module.hrm;

/**
 * HRM 独立启动入口 — <b>已禁止</b>（EXP-75 可执行部署约束）。
 * <p>
 * 入职资料依赖 {@code FileAccessApi} 本地 Bean（实现位于 infra-server）。
 * 支持形态：仅 {@code YudaoServerApplication} 单体（infra + hrm 同进程）。
 * <p>
 * 独立启动本类会立即失败，避免产出“可打包但缺 Bean”的假微服务。
 * 打包：本模块 spring-boot-maven-plugin 固定 {@code skip=true}，不生成独立可执行 jar。
 *
 * @author 宇擎源码
 */
public class HrmServerApplication {

    /**
     * 禁止 standalone 启动。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    public static void main(String[] args) {
        throw new UnsupportedOperationException(
                "EXP-75: Standalone HrmServerApplication is forbidden. "
                        + "Deploy via yudao-server monolith only (FileAccessApi co-located).");
    }

}
