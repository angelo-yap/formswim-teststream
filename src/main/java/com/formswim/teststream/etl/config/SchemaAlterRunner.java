package com.formswim.teststream.etl.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    public SchemaAlterRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
