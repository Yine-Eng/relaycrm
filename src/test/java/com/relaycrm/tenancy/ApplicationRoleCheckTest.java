package com.relaycrm.tenancy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import com.relaycrm.IntegrationTestBase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The startup check that refuses a database role able to get around row-level
 * security. It runs against the real PostgreSQL, with real roles.
 */
class ApplicationRoleCheckTest extends IntegrationTestBase {

    @Autowired
    DataSource applicationDataSource;

    @Test
    void theApplicationsOwnRolePasses() {
        assertThatCode(() -> TenancyConfig.checkApplicationRole(applicationDataSource)).doesNotThrowAnyException();
    }

    @Test
    void aSuperuserIsRefused() {
        // The test database's owner role is a superuser, like the local Docker one.
        DataSource owner = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        assertThatThrownBy(() -> TenancyConfig.checkApplicationRole(owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be a superuser or have BYPASSRLS");
    }

    /** No superuser, no BYPASSRLS, but it owns a table: still refused. */
    @Test
    void aRoleThatOwnsATableIsRefused() throws Exception {
        try (Connection admin = ownerConnection(); Statement sql = admin.createStatement()) {
            sql.execute("create role table_owner_probe login password 'probe' nosuperuser nobypassrls");
            sql.execute("create table owned_probe (id int)");
            sql.execute("alter table owned_probe owner to table_owner_probe");
            try {
                DataSource role = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "table_owner_probe", "probe");

                assertThatThrownBy(() -> TenancyConfig.checkApplicationRole(role))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("must not own any table")
                        .hasMessageContaining("owned_probe");
            } finally {
                sql.execute("drop table owned_probe");
                sql.execute("drop role table_owner_probe");
            }
        }
    }
}
