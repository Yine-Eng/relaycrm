package com.relaycrm.tenancy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TenancyConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareTransactionManager(emf);
    }

    /**
     * Refuse to start if the application's database role could get around
     * row-level security. Otherwise a misconfigured deployment (for example the
     * owner's credentials pasted into the app's settings) would silently lose
     * the database-level isolation. The role must not:
     *   - be a superuser or have BYPASSRLS: both skip every policy;
     *   - own any table in the schema: an owner is exempt from its own table's
     *     policies unless FORCE ROW LEVEL SECURITY is set (it is, today), and
     *     can switch FORCE off again.
     */
    @Bean
    public ApplicationRunner rowLevelSecurityCheck(DataSource dataSource) {
        return args -> checkApplicationRole(dataSource);
    }

    static void checkApplicationRole(DataSource dataSource) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "select rolsuper or rolbypassrls from pg_roles where rolname = current_user")) {
                if (!rs.next() || rs.getBoolean(1)) {
                    throw new IllegalStateException(
                            "The application database role must not be a superuser or have BYPASSRLS");
                }
            }
            try (ResultSet rs = s.executeQuery("""
                    select string_agg(c.relname, ', ' order by c.relname)
                    from pg_class c
                    join pg_namespace n on n.oid = c.relnamespace
                    where c.relowner = (select oid from pg_roles where rolname = current_user)
                      and c.relkind in ('r', 'p')
                      and n.nspname = current_schema()
                    """)) {
                rs.next();
                String owned = rs.getString(1);
                if (owned != null) {
                    throw new IllegalStateException("The application database role must not own any table, "
                            + "but it owns: " + owned + ". Run the migrations as a separate owner role");
                }
            }
        }
    }
}
