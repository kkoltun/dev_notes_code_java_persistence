package com.forum;

import org.hibernate.Session;

public class DatabaseConfiguration {
    private final HibernateConfiguration hibernateConfiguration;
    private final TransactionManagerConfiguration transactionManagerConfiguration;

    public DatabaseConfiguration(String dialect, String driver, String jdbcUrl, String userName, String password) {
        transactionManagerConfiguration = new TransactionManagerConfiguration(jdbcUrl, userName, password);
        hibernateConfiguration = new HibernateConfiguration(dialect, driver, jdbcUrl, userName, password);
    }

    public Session getSession() {
        return hibernateConfiguration.getSession();
    }

    public void stop() {
        transactionManagerConfiguration.stop();
    }
}
