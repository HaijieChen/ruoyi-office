package cn.iocoder.yudao.module.hrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HRM 独立启动入口（微服务形态）。
 * <p>
 * <b>EXP-75 部署约束：</b>入职资料依赖 {@code FileAccessApi} 本地 Bean（实现位于 infra-server），
 * <b>仅支持 yudao-server 单体</b>（infra + hrm 同进程）。独立启动本类无法注入 FileAccessApi，
 * 不属于 EXP-75 支持的部署形态。生产/测试请使用 {@code YudaoServerApplication}。
 *
 * @author 宇擎源码
 */
@SpringBootApplication
public class HrmServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrmServerApplication.class, args);
    }

}
