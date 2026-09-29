package com.example.erp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "logging.level.root=WARN"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FreshDatabaseMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("erp_migration_test")
            .withUsername("erp_test")
            .withPassword("erp_test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void freshInstallAndExistingDatabaseUpgradePreserveBusinessData() throws Exception {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT to_regclass('ecommerce.stripe_checkout')", String.class))
                .isNotNull();
        assertThat(jdbc.queryForObject("SELECT to_regclass('pos.stripe_checkout_legacy')", String.class))
                .isNotNull();

        UUID productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory.product
                    (id, sku, name, unit_type, perishable, reorder_point, vat_rate, online_visible)
                VALUES (?, 'MIGRATION-TEST', 'Migration test product', 'EACH', false, 0, 19.00, true)
                """, productId);

        // A database already at the latest version can lack the added V2.1 baseline
        // in its history. Reapply it against populated tables to verify upgrades.
        jdbc.update("DELETE FROM platform.flyway_schema_history WHERE version = '2.1'");
        new FlywayConfig().flyway(dataSource).migrate();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory.product WHERE id = ?", Long.class, productId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM platform.flyway_schema_history WHERE version = '2.1' AND success", Long.class))
                .isEqualTo(1L);
    }
}
