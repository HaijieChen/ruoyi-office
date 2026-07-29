package cn.iocoder.yudao.module.finance.service.role;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 财务角色迁移契约测试。
 *
 * 菜单主键由各环境自行生成，角色授权只能依赖路径、组件和权限码等稳定业务键。
 */
class FinanceRoleMigrationContractTest {

    private static final String MIGRATION_FILE = "sql/mysql/finance_roles_finance_admin_and_business_staff.sql";
    private static final Pattern ENVIRONMENT_SPECIFIC_MENU_IDS =
            Pattern.compile("\\b(?:5225|5228|5229|5245)\\b");

    @Test
    void migrationShouldNotDependOnEnvironmentSpecificMenuIds() throws IOException {
        String sql = readMigration();

        assertFalse(ENVIRONMENT_SPECIFIC_MENU_IDS.matcher(sql).find(),
                "角色迁移不能依赖本地数据库生成的菜单 ID");
    }

    @Test
    void migrationShouldLocateDirectoryAndPagesByStableBusinessKeys() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("m.`parent_id` = 0") && sql.contains("m.`path` = '/finance'"),
                "财务根目录必须通过 parent_id=0 与 /finance 路径定位");
        for (String component : List.of(
                "finance/receipt/index",
                "finance/business-order/index",
                "finance/receipt-claim/index")) {
            assertTrue(sql.contains("'" + component + "'"),
                    "商务人员角色必须通过稳定 component 定位页面：" + component);
        }
        assertTrue(sql.contains("m.`component` LIKE 'finance/%'"),
                "财务管理员必须通过 finance/* 组件前缀获得全部财务页面（含复核页）");
        assertTrue(sql.contains("m.`permission` LIKE 'finance:%'"),
                "财务管理员必须通过 finance:* 权限前缀获得全部财务按钮权限");
    }

    private static String readMigration() throws IOException {
        Path migration = findRepositoryRoot().resolve(MIGRATION_FILE);
        assertTrue(Files.exists(migration), "财务角色迁移文件必须存在");
        return Files.readString(migration);
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("无法定位仓库根目录");
        }
        return current;
    }
}
