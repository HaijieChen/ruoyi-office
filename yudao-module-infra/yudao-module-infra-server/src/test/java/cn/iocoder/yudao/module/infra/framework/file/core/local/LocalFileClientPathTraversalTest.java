package cn.iocoder.yudao.module.infra.framework.file.core.local;

import cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #5：LocalFileClient 拒绝路径穿越。
 */
class LocalFileClientPathTraversalTest {

    @TempDir
    Path tempDir;

    private LocalFileClient client() {
        LocalFileClientConfig config = new LocalFileClientConfig();
        config.setBasePath(tempDir.toAbsolutePath().toString());
        config.setDomain("http://127.0.0.1");
        LocalFileClient client = new LocalFileClient(1L, config);
        client.init();
        return client;
    }

    @Test
    void rejectsPathTraversalOutsideBase() throws Exception {
        Path secret = tempDir.getParent().resolve("secret-outside.txt");
        Files.writeString(secret, "SECRET", StandardCharsets.UTF_8);
        try {
            LocalFileClient client = client();
            String traversal = ".." + File.separator + secret.getFileName();
            assertThrows(IllegalArgumentException.class, () -> client.getContent(traversal));
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    void allowsPathInsideBase() throws Exception {
        LocalFileClient client = client();
        Path nested = tempDir.resolve("hrm-onboarding-private");
        Files.createDirectories(nested);
        Path file = nested.resolve("ok.pdf");
        Files.write(file, new byte[]{1, 2, 3});
        byte[] content = client.getContent("hrm-onboarding-private" + File.separator + "ok.pdf");
        assertArrayEquals(new byte[]{1, 2, 3}, content);
    }

}
