package cn.iocoder.yudao.module.finance.service.claim;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 财务到款认领迁移契约测试。
 *
 * 保护 sql/mysql/finance_receipt_claim_phase1c.sql 中的菜单注册约定：
 * - 必须存在 type-2 页面菜单（到款认领），路由 /finance/receipt-claim
 * - 权限菜单必须挂载到该页面菜单下，而非 finance/receipt/index
 * - 菜单 SQL 必须具备幂等性（WHERE NOT EXISTS / UPDATE 收敛）
 */
class FinanceReceiptClaimMigrationContractTest {

    @Test
    void migrationShouldRegisterPageMenuForReceiptClaim() throws IOException {
        Path migration = findRepositoryRoot().resolve("sql/mysql/finance_receipt_claim_phase1c.sql");
        assertTrue(Files.exists(migration), "phase1c 迁移文件必须存在");

        String sql = Files.readString(migration);
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        // 页面菜单：type = 2，component 为 finance/receipt-claim/index
        assertTrue(sql.contains("`component` = 'finance/receipt-claim/index'"),
                "必须包含针对 finance/receipt-claim/index 的页面菜单 INSERT/UPDATE");
        assertTrue(sql.contains("'FinanceReceiptClaimMyPage'"),
                "component_name 必须为 FinanceReceiptClaimMyPage");
        // sort = 3（在页面菜单 INSERT 语句中）
        assertTrue(lowerSql.contains(", 2, 3, parent_menu.id, 'receipt-claim'")
                        || lowerSql.contains(", 2, 3,")
                        || sql.contains("SELECT '到款认领', '', 2, 3,"),
                "页面菜单 sort 必须为 3");
        // 路由路径 receipt-claim
        assertTrue(sql.contains("'receipt-claim'"),
                "页面菜单 path 必须为 receipt-claim");

        // 幂等 INSERT（WHERE NOT EXISTS）
        assertTrue(lowerSql.contains("where not exists"),
                "必须使用 WHERE NOT EXISTS 保证幂等");
        // 收敛 UPDATE
        assertTrue(lowerSql.contains("update `system_menu`"),
                "必须包含 UPDATE 收敛语句");

        // 验证页面菜单的 type = 2 标记
        assertTrue(sql.contains(", 2, ") || sql.contains(",2,"),
                "必须包含 type=2 的页面菜单行");
    }

    @Test
    void permissionMenusShouldBeParentedToPageMenuNotReceiptIndex() throws IOException {
        Path migration = findRepositoryRoot().resolve("sql/mysql/finance_receipt_claim_phase1c.sql");
        String sql = Files.readString(migration);
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        // 权限菜单必须存在
        List<String> permissions = List.of(
                "finance:receipt-claim:query",
                "finance:receipt-claim:create",
                "finance:receipt-claim:update",
                "finance:receipt-claim:resubmit",
                "finance:receipt-claim:review",
                "finance:receipt-claim:confirm",
                "finance:receipt-claim:reject"
        );
        for (String perm : permissions) {
            assertTrue(sql.contains("'" + perm + "'"),
                    "必须包含权限 " + perm);
        }

        // 权限菜单必须挂载到 receipt-claim 页面菜单（通过 component = 'finance/receipt-claim/index' 定位）
        // 而不是 finance/receipt/index
        assertTrue(sql.contains("component` = 'finance/receipt-claim/index' LIMIT 1) page_menu"),
                "权限菜单 INSERT 的 page_menu 子查询必须使用 finance/receipt-claim/index 定位");
        // UPDATE 收敛也必须指向 receipt-claim 页面
        assertTrue(sql.contains("`component` = 'finance/receipt-claim/index' LIMIT 1) page_menu"),
                "权限菜单 UPDATE 的 page_menu 子查询必须使用 finance/receipt-claim/index 定位");

        // 确保旧的 finance/receipt/index 引用已被替换（不应再作为权限菜单的父菜单）
        // 注意：其他地方引用 finance/receipt/index 是允许的，只是权限菜单不应引用
        // 检查：不存在以 finance/receipt/index 作为权限菜单父菜单的 INSERT 语句
        assertFalse(
                lowerSql.contains("component` = 'finance/receipt/index' limit 1) page_menu")
                        && lowerSql.contains("finance:receipt-claim:"),
                "权限菜单不应再挂载到 finance/receipt/index 页面下");
    }

    @Test
    void migrationShouldPreserveExistingDdlContent() throws IOException {
        Path migration = findRepositoryRoot().resolve("sql/mysql/finance_receipt_claim_phase1c.sql");
        String sql = Files.readString(migration);

        // 保留已有的 DDL：表结构
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `finance_receipt_claim`"),
                "必须保留 finance_receipt_claim 建表语句");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `finance_receipt_claim_item`"),
                "必须保留 finance_receipt_claim_item 建表语句");

        // 保留已有的 ALTER TABLE 幂等列添加
        assertTrue(sql.contains("`reject_reason`"),
                "必须保留 reject_reason 列的幂等添加");
        assertTrue(sql.contains("`reviewer_id`"),
                "必须保留 reviewer_id 列的幂等添加");
        assertTrue(sql.contains("`review_time`"),
                "必须保留 review_time 列的幂等添加");

        // 保留 finance_business_order 的 confirmed_claimed_amount 列
        assertTrue(sql.contains("`confirmed_claimed_amount`"),
                "必须保留 confirmed_claimed_amount 列的幂等添加");
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("无法从 " + Path.of("").toAbsolutePath() + " 定位仓库根目录");
        }
        return current;
    }

}
