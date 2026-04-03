package com.formswim.teststream.shared.config;

import java.sql.Connection;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.formswim.teststream.workspace.services.FolderBackfillService;

/**
 * Runs once on startup to widen any varchar(255) columns that need to be TEXT.
 * Hibernate's ddl-auto=update never alters existing column types, so this
 * handles the migration for columns that were created before TEXT annotations
 * were added.
 */
@Component
public class SchemaAlterRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaAlterRunner.class);

    private final JdbcTemplate jdbc;
    private final FolderBackfillService folderBackfillService;

    public SchemaAlterRunner(JdbcTemplate jdbc,
                             FolderBackfillService folderBackfillService) {
        this.jdbc = jdbc;
        this.folderBackfillService = folderBackfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        alterColumnToText("test_step", "step_summary");
        alterColumnToText("test_step", "test_data");
        alterColumnToText("test_step", "expected_result");
        alterColumnToText("test_case", "summary");
        alterColumnToText("test_case", "description");
        alterColumnToText("test_case", "precondition");
        alterColumnToText("test_case", "labels");
        alterColumnToText("test_case", "folder");
        alterColumnToText("test_case", "story_linkages");

        // Older schemas enforced work_key uniqueness globally. The application model is
        // (team_key, work_key) unique, so migrate the constraint for Postgres.
        ensureTestCaseWorkKeyUniquePerTeam();

        try {
            int createdFolders = folderBackfillService.backfillFoldersFromTestCases();
            log.info("Folder backfill completed. Created {} folder rows.", createdFolders);
        } catch (Exception exception) {
            log.warn("Folder backfill failed.", exception);
        }
    }

    private void ensureTestCaseWorkKeyUniquePerTeam() {
        if (!isPostgres()) {
            return;
        }

        try {
            // Remove the old global uniqueness constraint if it exists.
            // Safe to run repeatedly.
            jdbc.execute("ALTER TABLE test_case DROP CONSTRAINT IF EXISTS test_case_work_key_key");

            // Ensure the team-scoped unique constraint exists.
            if (!constraintExists("test_case", "uk_test_case_team_work_key")) {
                jdbc.execute("ALTER TABLE test_case ADD CONSTRAINT uk_test_case_team_work_key UNIQUE (team_key, work_key)");
                log.info("Added constraint uk_test_case_team_work_key on test_case(team_key, work_key)");
            }
        } catch (Exception e) {
            log.warn("Could not migrate test_case work_key uniqueness constraint: {}", e.getMessage());
        }
    }

    private boolean constraintExists(String tableName, String constraintName) {
        try {
            String sql = """
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_name = ? AND constraint_name = ?
                    LIMIT 1
                    """;
            return !jdbc.queryForList(sql, tableName, constraintName).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPostgres() {
        try {
            if (jdbc.getDataSource() == null) {
                return false;
            }
            try (Connection connection = jdbc.getDataSource().getConnection()) {
                String product = connection.getMetaData().getDatabaseProductName();
                return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Widens a column to TEXT only if it is currently character varying.
     * Safe to run repeatedly – does nothing if the column is already TEXT.
     */
    private void alterColumnToText(String table, String column) {
        try {
            String check = """
                    SELECT data_type FROM information_schema.columns
                    WHERE table_name = ? AND column_name = ?
                    """;
            var rows = jdbc.queryForList(check, table, column);
            if (rows.isEmpty()) return;

            String dataType = (String) rows.get(0).get("data_type");
            if ("text".equalsIgnoreCase(dataType)) return;

            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT");
            log.info("Altered {}.{} to TEXT (was {})", table, column, dataType);
        } catch (Exception e) {
            log.warn("Could not alter {}.{}: {}", table, column, e.getMessage());
        }
    }
}
