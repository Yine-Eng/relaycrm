package com.relaycrm.tenancy;

import java.sql.Connection;
import java.sql.ResultSet;
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
     * Refuse to start if the application's database role could bypass row-level
     * security (superuser or BYPASSRLS). Otherwise a misconfigured deployment
     * would silently lose the database-level isolation.
     */
    @Bean
    public ApplicationRunner rowLevelSecurityCheck(DataSource dataSource) {
        return args -> {
            try (Connection c = dataSource.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "select rolsuper or rolbypassrls from pg_roles where rolname = current_user")) {
                if (!rs.next() || rs.getBoolean(1)) {
                    throw new IllegalStateException(
                            "The application database role must not be a superuser or have BYPASSRLS");
                }
            }
        };
    }
}
