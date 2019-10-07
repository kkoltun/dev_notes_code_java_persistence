package com.hr;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.UserTransaction;

class TransactionManagerSetup {
  private static final String DATASOURCE_NAME = "hrAppDataSource";
  private static final String SERVER_ID = "myServer";
  private static final String BITRONIX_XA_DATASOURCE =
      "bitronix.tm.resource.jdbc.lrc.LrcXADataSource";
  private static final String JDBC_URL =
      "jdbc:mysql://localhost:3306/hr?sessionVariables=sql_mode='PIPES_AS_CONCAT'";
  private static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

  private final Context context = new InitialContext();

  TransactionManagerSetup() throws Exception {
    TransactionManagerServices.getConfiguration().setServerId(SERVER_ID);
    TransactionManagerServices.getConfiguration().setDisableJmx(true);
    TransactionManagerServices.getConfiguration().setJournal("null");

    PoolingDataSource datasource = new PoolingDataSource();
    datasource.setUniqueName(DATASOURCE_NAME);
    datasource.setMinPoolSize(1);
    datasource.setMaxPoolSize(5);
    datasource.setPreparedStatementCacheSize(10);

    // Hibernate's SQL schema generator calls connection.setAutoCommit(true)
    // and we use auto-commit mode when the EntityManager is in suspended
    // mode and not joined with a transaction.
    datasource.setAllowLocalTransactions(true);

    datasource.setClassName(BITRONIX_XA_DATASOURCE);
    datasource.getDriverProperties().put("url", JDBC_URL);
    datasource.getDriverProperties().put("driverClassName", DRIVER_CLASS_NAME);
    datasource.getDriverProperties().put("user", "hr");
    datasource.getDriverProperties().put("password", "hr");
    datasource.init();
  }

  UserTransaction getUserTransaction() {
    try {
      return (UserTransaction) getNamingContext().lookup("java:comp/UserTransaction");
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private Context getNamingContext() {
    return context;
  }
}
