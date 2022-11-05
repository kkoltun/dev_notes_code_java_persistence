package dev.karolkoltun.persistence.jpa;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.Status;
import javax.transaction.UserTransaction;

class InMemoryTransactionManagerSetup {
  private static final String HIBERNATE_DIALECT = org.hibernate.dialect.H2Dialect.class.getName();
  private static final String DATASOURCE_NAME = "hrAppDataSource";
  private static final String SERVER_ID = "myServer";
  private static final String JDBC_URL =
      "jdbc:h2:mem:test";

  private final Context context = new InitialContext();
  private final PoolingDataSource datasource;

  InMemoryTransactionManagerSetup() throws Exception {
    TransactionManagerServices.getConfiguration().setServerId(SERVER_ID);
    TransactionManagerServices.getConfiguration().setDisableJmx(true);
    TransactionManagerServices.getConfiguration().setJournal("null");

    datasource = new PoolingDataSource();
    datasource.setUniqueName(DATASOURCE_NAME);
    datasource.setMinPoolSize(1);
    datasource.setMaxPoolSize(5);
    datasource.setPreparedStatementCacheSize(10);

    // Hibernate's SQL schema generator calls connection.setAutoCommit(true)
    // and we use auto-commit mode when the EntityManager is in suspended
    // mode and not joined with a transaction.
    datasource.setAllowLocalTransactions(true);

    datasource.setClassName("org.h2.jdbcx.JdbcDataSource");
    datasource.getDriverProperties().put("url", JDBC_URL);
    datasource.getDriverProperties().put("user", "user");
    datasource.getDriverProperties().put("password", "sa");
    datasource.init();
  }

  UserTransaction getUserTransaction() {
    try {
      return (UserTransaction) getNamingContext().lookup("java:comp/UserTransaction");
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  String getHibernateDialect() {
    return HIBERNATE_DIALECT;
  }

  void rollback() {
    UserTransaction tx = getUserTransaction();
    try {
      if (tx.getStatus() == Status.STATUS_ACTIVE ||
          tx.getStatus() == Status.STATUS_MARKED_ROLLBACK)
        tx.rollback();
    } catch (Exception ex) {
      System.err.println("Rollback of transaction failed, trace follows!");
      ex.printStackTrace(System.err);
    }
  }

  void stop() {
    datasource.close();
    TransactionManagerServices.getTransactionManager().shutdown();
  }

  private Context getNamingContext() {
    return context;
  }
}
