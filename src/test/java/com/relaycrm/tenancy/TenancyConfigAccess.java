package com.relaycrm.tenancy;

import java.sql.SQLException;

import javax.sql.DataSource;

/** Lets tests in other packages run the startup role check against any database. */
public final class TenancyConfigAccess {

    private TenancyConfigAccess() {
    }

    public static void checkApplicationRole(DataSource dataSource) throws SQLException {
        TenancyConfig.checkApplicationRole(dataSource);
    }
}
