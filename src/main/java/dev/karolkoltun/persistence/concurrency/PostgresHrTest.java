package dev.karolkoltun.persistence.concurrency;

import dev.karolkoltun.persistence.DataSourceProvider;
import dev.karolkoltun.persistence.HibernateTest;
import dev.karolkoltun.persistence.PostgresqlHrDataSourceProvider;

public class PostgresHrTest extends HibernateTest {
    @Override
    public DataSourceProvider dataSourceProvider() {
        return new PostgresqlHrDataSourceProvider();
    }

    @Override
    public boolean recreateBeforeEachTest() {
        return false;
    }
}
