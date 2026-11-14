package com.relaycrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import com.relaycrm.tenancy.TenancyConfigAccess;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Every other test runs the migrations as a superuser owner (like local Docker), which row-level
 * security never applies to. Hosted databases (Neon) give you an owner that is NOT a superuser,
 * and with FORCE ROW LEVEL SECURITY the policies then apply to the migrations and the seed too.
 * This test builds that setup in a separate database and runs everything the way the hosted demo
 * will: migrations and seed as the non-superuser owner, the application as relaycrm_app.
 */
class NonSuperuserOwnerTest extends IntegrationTestBase {

    private static final String DATABASE = "hosted_like";
    private static final String OWNER = "hosted_owner";
    private static final String OWNER_PASSWORD = "hosted-owner-pw";

    private static String url;

    @BeforeAll
    static void createDatabaseWithANonSuperuserOwner() throws Exception {
        try (Connection admin = ownerConnection(); Statement sql = admin.createStatement()) {
            sql.execute("create role " + OWNER + " login password '" + OWNER_PASSWORD
                    + "' nosuperuser nobypassrls createrole");
            // The app role, as docs/deploy/neon-setup.sql creates it. Roles belong to the whole server,
            // so it may already exist (created by the main test database's migration).
            sql.execute("""
                    do $$ begin
                      if not exists (select from pg_roles where rolname = 'relaycrm_app') then
                        create role relaycrm_app login password 'app_secret'
                          nosuperuser nobypassrls nocreatedb nocreaterole noreplication noinherit;
                      end if;
                    end $$""");
            sql.execute("create database " + DATABASE + " owner " + OWNER);
            // What docs/deploy/neon-setup.sql gives the app role (it already exists in this cluster).
            sql.execute("grant connect on database " + DATABASE + " to relaycrm_app");
        }
        url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + DATABASE);
        try (Connection owner = new DriverManagerDataSource(url, OWNER, OWNER_PASSWORD).getConnection();
             Statement sql = owner.createStatement()) {
            sql.execute("grant usage on schema public to relaycrm_app");
        }
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        try (Connection admin = ownerConnection(); Statement sql = admin.createStatement()) {
            sql.execute("drop database if exists " + DATABASE + " with (force)");
            sql.execute("drop owned by " + OWNER);
            sql.execute("drop role if exists " + OWNER);
        }
    }

    @Test
    void migrationsAndSeedWorkAsANonSuperuserOwnerAndTheAppRolePassesItsCheck() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(url, OWNER, OWNER_PASSWORD)
                .locations("classpath:db/migration", "classpath:db/demo")
                .placeholders(Map.of("appUser", "relaycrm_app", "appPassword", "app_secret"))
                .load();

        assertThatCode(flyway::migrate).doesNotThrowAnyException();
        // Running the seed again (as a deploy after an edit to it would) also works.
        assertThatCode(() -> {
            flyway.repair();
            flyway.migrate();
        }).doesNotThrowAnyException();

        DataSource app = new DriverManagerDataSource(url, "relaycrm_app", "app_secret");
        assertThatCode(() -> TenancyConfigAccess.checkApplicationRole(app)).doesNotThrowAnyException();

        // Each organization sees exactly its own seeded rows.
        assertThat(countAsOrganization(app, 1, "contacts")).isEqualTo(2);
        assertThat(countAsOrganization(app, 2, "contacts")).isEqualTo(1);
        assertThat(countAsOrganization(app, 1, "opportunities")).isEqualTo(1);
        assertThat(countAsOrganization(app, 2, "leads")).isEqualTo(1);

        // Ids handed out next do not collide with the seed.
        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(url, OWNER, OWNER_PASSWORD));
        assertThat(owner.queryForObject(
                "select last_value from " + "contacts_id_seq", Long.class)).isGreaterThanOrEqualTo(100L);
    }

    /** Counts rows the way the application sees them: as relaycrm_app, with app.current_org set. */
    private static long countAsOrganization(DataSource app, long orgId, String table) throws Exception {
        try (Connection c = app.getConnection()) {
            c.setAutoCommit(false);
            try (Statement sql = c.createStatement()) {
                sql.execute("select set_config('app.current_org', '" + orgId + "', true)");
                try (var rows = sql.executeQuery("select count(*) from " + table)) {
                    rows.next();
                    return rows.getLong(1);
                }
            } finally {
                c.rollback();
            }
        }
    }
}
