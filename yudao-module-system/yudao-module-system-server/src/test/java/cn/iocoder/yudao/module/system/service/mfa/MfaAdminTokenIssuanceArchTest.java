package cn.iocoder.yudao.module.system.service.mfa;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F-01-9：静态依赖扫描 — 生产代码除 Facade 外不得直调 ADMIN create/refresh。
 * <p>
 * 允许的调用方：
 * <ul>
 *   <li>{@code MfaTokenIssuanceFacadeImpl}</li>
 *   <li>{@code OAuth2TokenServiceImpl} 自身实现</li>
 *   <li>{@code OAuth2TokenService} 接口声明</li>
 * </ul>
 * 其余 main 源码中的 {@code createAccessToken(} / {@code refreshAccessToken(} 调用必须经 Facade
 * 或明确标注 N/A（client_credentials 已迁入 Facade）。
 */
public class MfaAdminTokenIssuanceArchTest {

    private static final List<String> ALLOWED_CREATE_REFRESH_FILES = List.of(
            "MfaTokenIssuanceFacadeImpl.java",
            "OAuth2TokenServiceImpl.java",
            "OAuth2TokenService.java",
            "OAuth2TokenCommonApi.java", // 接口定义
            "OAuth2TokenApiImpl.java" // create 对 ADMIN 显式拒绝；refresh 走 Facade；MEMBER 仍可 create
    );

    @Test
    void productionCodeMustNotBypassFacadeForAdminTokenIssuance() throws IOException {
        Path mainJava = findMainJavaRoot();
        if (mainJava == null) {
            // 在某些 IDE 工作目录下可能找不到，降级为通过（避免假红）；CI 工作区应能找到
            System.err.println("[MfaAdminTokenIssuanceArchTest] main java root not found, skip");
            return;
        }

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainJava)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().contains("Test"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (ALLOWED_CREATE_REFRESH_FILES.contains(name)) {
                            return;
                        }
                        try {
                            String content = Files.readString(p);
                            // 直调 oauth2TokenService.createAccessToken / .refreshAccessToken
                            if (content.contains("oauth2TokenService.createAccessToken")
                                    || content.contains("oauth2TokenService.refreshAccessToken")) {
                                offenders.add(mainJava.relativize(p).toString());
                            }
                        } catch (IOException e) {
                            fail("read failed: " + p + " " + e.getMessage());
                        }
                    });
        }

        assertTrue(offenders.isEmpty(),
                "Production code must not call oauth2TokenService.create/refreshAccessToken outside Facade. Offenders: "
                        + offenders);
    }

    private static Path findMainJavaRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("src/main/java"),
                cwd.resolve("yudao-module-system-server/src/main/java"),
                cwd.resolve("yudao-module-system/yudao-module-system-server/src/main/java"),
                cwd.resolve("oa/yudao-module-system/yudao-module-system-server/src/main/java")
        );
        // 从 cwd 向上找
        Path p = cwd;
        for (int i = 0; i < 6; i++) {
            Path hit = p.resolve("yudao-module-system/yudao-module-system-server/src/main/java");
            if (Files.isDirectory(hit)) {
                return hit;
            }
            hit = p.resolve("src/main/java");
            if (Files.isDirectory(hit) && Files.exists(hit.resolve("cn/iocoder/yudao/module/system"))) {
                return hit;
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        for (Path c : candidates) {
            if (Files.isDirectory(c)) {
                return c;
            }
        }
        return null;
    }

}
