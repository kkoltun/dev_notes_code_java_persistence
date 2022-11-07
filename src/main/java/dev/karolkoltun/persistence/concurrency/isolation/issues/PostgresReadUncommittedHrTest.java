package dev.karolkoltun.persistence.concurrency.isolation.issues;

import dev.karolkoltun.persistence.DataSourceProvider;
import dev.karolkoltun.persistence.HibernateTest;
import dev.karolkoltun.persistence.PostgresqlHrDataSourceProvider;

import java.sql.Connection;

public class PostgresReadUncommittedHrTest extends HibernateTest {
    @Override
    public Integer getIsolationLevel() {
        return Connection.TRANSACTION_READ_UNCOMMITTED;
    }

    @Override
    public DataSourceProvider dataSourceProvider() {
        return new PostgresqlHrDataSourceProvider();
    }

    @Override
    public boolean recreateBeforeEachTest() {
        return false;
    }
}
