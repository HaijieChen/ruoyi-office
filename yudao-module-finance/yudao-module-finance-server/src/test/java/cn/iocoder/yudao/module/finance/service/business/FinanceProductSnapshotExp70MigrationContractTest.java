package cn.iocoder.yudao.module.finance.service.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXP-70 包①：产品快照 DDL 幂等与镜像路径契约。
 */
class FinanceProductSnapshotExp70MigrationContractTest {

    @Test
    void productSnapshotMigrationShouldBeIdempotentAndMirrored() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path primary = repositoryRoot.resolve("sql/mysql/finance_product_snapshot_exp70.sql");
        Path mirrored = repositoryRoot.resolve("ruoyi-office-vben/sql/mysql/finance_product_snapshot_exp70.sql");

        assertTrue(Files.exists(primary), "EXP-70 migration must exist at sql/mysql/");
        assertTrue(Files.exists(mirrored), "EXP-70 migration must be mirrored under ruoyi-office-vben/sql/mysql/");
        assertEquals(Files.readString(primary), Files.readString(mirrored), "mirrored DDL must be identical");

        String sql = Files.readString(primary);
        String lower = sql.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("information_schema"));
        assertTrue(lower.contains("product_type_snapshot"));
        assertTrue(lower.contains("source_contract_application_id"));
        assertTrue(lower.contains("idx_source_contract_application_id"));
        assertTrue(lower.contains("finance_business_order"));
        assertTrue(lower.contains("finance_invoice_application_line"));
        assertTrue(lower.contains("prepare"));
        // 只读审计注释存在，无静默 UPDATE 回填
        assertTrue(sql.contains("只读审计") || lower.contains("-- 3."));
        assertTrue(!lower.contains("update `finance_business_order` set `product_type_snapshot`")
                        || lower.contains("--"),
                "package ① must not force-backfill product snapshots");
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path p = current; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("sql/mysql"))
                    && Files.exists(p.resolve("yudao-module-finance"))) {
                return p;
            }
        }
        throw new IllegalStateException("cannot locate repository root from " + current);
    }
}
