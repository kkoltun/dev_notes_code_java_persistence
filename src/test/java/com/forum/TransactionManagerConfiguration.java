package com.forum;

import com.zaxxer.hikari.HikariDataSource;

class TransactionManagerConfiguration {

    private final HikariDataSource datasource;

    TransactionManagerConfiguration(String jdbcUrl, String userName, String password) {
        datasource = new HikariDataSource();
        datasource.setJdbcUrl(jdbcUrl);
        datasource.setUsername(userName);
        datasource.setPassword(password);
    }

    void stop() {
        datasource.close();
    }

}
