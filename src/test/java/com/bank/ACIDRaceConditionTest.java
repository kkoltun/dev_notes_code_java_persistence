package com.bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ACIDRaceConditionTest extends HibernateTest {

  private static final Logger log = LoggerFactory.getLogger(ACIDRaceConditionTest.class);
  private static final String BOB_IBAN = "Bob-456";
  private static final String ALICE_IBAN = "Alice-123";

  // -- Test configuration details --
  private static final boolean USE_JPA = false;

  @Override
  DataSourceProvider dataSourceProvider() {
    return new PostgresqlDataSourceProvider();
  }
  // --

  @BeforeEach
  void createData() {
    createAccounts();
  }

  @AfterEach
  void destroyData() {
    destroyAccounts();
  }

  // STEP 1: We create serial tests and everything works!
  @Test
  void testSerialExecution() {
    assertEquals(10, getBalanceUsingJPA(ALICE_IBAN));
    assertEquals(0, getBalanceUsingJPA(BOB_IBAN));

    transferWithoutTransaction(ALICE_IBAN, BOB_IBAN, 5);

    assertEquals(5, getBalanceUsingJPA(ALICE_IBAN));
    assertEquals(5, getBalanceUsingJPA(BOB_IBAN));

    transferWithoutTransaction(ALICE_IBAN, BOB_IBAN, 5);

    assertEquals(0, getBalanceUsingJPA(ALICE_IBAN));
    assertEquals(10, getBalanceUsingJPA(BOB_IBAN));

    transferWithoutTransaction(ALICE_IBAN, BOB_IBAN, 5);

    assertEquals(0, getBalanceUsingJPA(ALICE_IBAN));
    assertEquals(10, getBalanceUsingJPA(BOB_IBAN));
  }

  // STEP 2: Whoops! We did not cover concurrency.
  @Test
  void testParallelExecutionWithoutTransaction() {
    int aliceBefore = getBalanceUsingJPA(ALICE_IBAN);
    int bobBefore = getBalanceUsingJPA(BOB_IBAN);
    log.info("Before: Alice {}, Bob {}", aliceBefore, bobBefore);
    assertEquals(10, aliceBefore);
    assertEquals(0, bobBefore);

    parallelExecutionWithoutTransaction();

    int aliceAfter = getBalanceUsingJPA(ALICE_IBAN);
    int bobAfter = getBalanceUsingJPA(BOB_IBAN);
    log.info("After: Alice {}, Bob {}", aliceAfter, bobAfter);
    assertEquals(0, aliceAfter);
    assertEquals(10, bobAfter);
  }

  // STEP 3: OK, so there should be a transaction, now everything should be working.
  @Test
  void testParallelExecutionWithTransactionAndDefaultIsolationLevel() {
    int aliceBefore = getBalanceUsingJPA(ALICE_IBAN);
    int bobBefore = getBalanceUsingJPA(BOB_IBAN);
    log.info("Before: Alice {}, Bob {}", aliceBefore, bobBefore);
    assertEquals(10, aliceBefore);
    assertEquals(0, bobBefore);

    parallelExecutionWithTransactionAndDefaultIsolationLevel();

    int aliceAfter = getBalanceUsingJPA(ALICE_IBAN);
    int bobAfter = getBalanceUsingJPA(BOB_IBAN);
    log.info("After: Alice {}, Bob {}", aliceAfter, bobAfter);
    assertEquals(0, aliceAfter);
    assertEquals(10, bobAfter);
  }

  // STEP 4: That still did not work! It is because of a lost update (Vlad says that; I would say that it was more a unrepeatable read). Let's learn about the isolation levels first...
  @Test
  void testParallelExecutionWithTransactionAndRepeatableReadIsolationLevel() {
    assertEquals(10, getBalance(ALICE_IBAN));
    assertEquals(0, getBalance(BOB_IBAN));

    parallelExecutionWithTransactionAndRepeatableReadIsolationLevel();

    assertEquals(0, getBalance(ALICE_IBAN));
    assertEquals(10, getBalance(BOB_IBAN));
  }

  void transferWithoutTransaction(String fromIban, String toIban, int transferredDolars) {
    // The entire operation is done without a single transaction.
    Integer fromBalance = getBalance(fromIban);

    if (fromBalance >= transferredDolars) {
      add(fromIban, -transferredDolars);
      add(toIban, transferredDolars);
    }
  }

  public void parallelExecutionWithoutTransaction() {
    int threadCount = 8;

    String fromIban = ALICE_IBAN;
    String toIban = BOB_IBAN;
    int transferredDollars = 5;

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(() -> {
        awaitOnLatch(startLatch);

        transferWithoutTransaction(fromIban, toIban, transferredDollars);

        endLatch.countDown();
      }).start();
    }

    log.info("Starting threads...");
    startLatch.countDown();
    awaitOnLatch(endLatch);
  }

  public void parallelExecutionWithTransactionAndDefaultIsolationLevel() {
    parallelExecutionWithTransaction(null);
  }

  public void parallelExecutionWithTransactionAndRepeatableReadIsolationLevel() {
    parallelExecutionWithTransaction(Connection.TRANSACTION_REPEATABLE_READ);
  }

  public void parallelExecutionWithTransaction(Integer isolationLevel) {
    int threadCount = 2;

    String fromIban = ALICE_IBAN;
    String toIban = BOB_IBAN;
    int transferredDollars = 10;

    CountDownLatch workerThreadWaitsAfterReadingBalanceLatch = new CountDownLatch(threadCount);
    CountDownLatch workerThreadWriteBalanceLatch = new CountDownLatch(1);
    CountDownLatch allWorkerThreadsHaveFinishedLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(() -> {
        try {
          doInJDBC(connection -> {
            if (isolationLevel != null) {
              setIsolationLevel(connection, isolationLevel);
            }
            printConnectionDetails(connection);

            workerThreadWaitsAfterReadingBalanceLatch.countDown();
            awaitOnLatch(workerThreadWriteBalanceLatch);

            log.info("start");

            int fromBalance = getBalance(connection, fromIban);
            log.info("getbalance: [{}: {}].", fromIban, fromBalance);

            if (fromBalance >= transferredDollars) {
              add(connection, fromIban, -transferredDollars);
              add(connection, toIban, transferredDollars);
            }

            log.info("end");
          });
        } catch (Exception exception) {
          System.err.println("Error transferring money: " + exception.getMessage());
          exception.printStackTrace(System.err);
        }

        allWorkerThreadsHaveFinishedLatch.countDown();
      }).start();
    }

    log.info("Starting threads");
    awaitOnLatch(workerThreadWaitsAfterReadingBalanceLatch);
    workerThreadWriteBalanceLatch.countDown();
    awaitOnLatch(allWorkerThreadsHaveFinishedLatch);
  }

  private static void awaitOnLatch(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void createAccounts() {
    doInJpa(entityManager -> {
      Account bobAccount = new Account(BOB_IBAN, "Bob", 0);
      entityManager.persist(bobAccount);

      Account aliceAccount = new Account(ALICE_IBAN, "Alice", 10);
      entityManager.persist(aliceAccount);
    });
  }

  private void destroyAccounts() {
    doInJpa(entityManager -> {
      entityManager.createQuery("DELETE FROM Account")
          .executeUpdate();
    });
  }

  private Integer getBalance(String iban) {
    return USE_JPA
            ? getBalanceUsingJPA(iban)
            : getBalanceUsingJDBC(iban);
  }

  private Integer getBalance(Connection connection, String iban) {
    try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM account WHERE iban = ?")) {

      statement.setString(1, iban);
      ResultSet resultSet = statement.executeQuery();

      if (resultSet.next()) {
        return resultSet.getInt(1);
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
    throw new IllegalArgumentException(String.format("Could not get balance for account IBAN [%s].", iban));
  }

  private void add(String iban, int dollars) {
    if (USE_JPA) {
      addJPA(iban, dollars);
    } else {
      addJDBC(iban, dollars);
    }
  }

  private void add(Connection connection, String iban, int balance) {
    log.info("add: [{}: {} + {}].", iban, getBalance(connection, iban), balance);

    try (PreparedStatement statement = connection.prepareStatement("UPDATE account SET balance = balance + ? WHERE iban = ?")) {
      statement.setInt(1, balance);
      statement.setString(2, iban);

      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }

    log.info("after add: [{}: {}].", iban, getBalance(connection, iban));
  }

  private Integer getBalanceUsingJDBC(String iban) {
    return doInJDBC(connection -> {
      printConnectionDetails(connection);
      return getBalance(connection, iban);
    });
  }

  private Integer getBalanceUsingJPA(String iban) {
    return doInJpa(entityManager -> {
      return entityManager.createQuery("SELECT a.balance FROM Account a WHERE a.iban = :iban", Integer.class)
          .setParameter("iban", iban)
          .getSingleResult();
    });
  }

  private void addJDBC(String iban, int dollars) {
    doInJDBC(connection -> {
      printConnectionDetails(connection);
      add(connection, iban, dollars);
    });
  }

  private void addJPA(String iban, int dollars) {
    doInJpa(entityManager -> {
      entityManager.createQuery("UPDATE Account a SET a.balance = a.balance + :dollars WHERE a.iban = :iban")
          .setParameter("dollars", dollars)
          .setParameter("iban", iban)
          .executeUpdate();
    });
  }

  private void setIsolationLevel(Connection connection, int level) {
    try {
      connection.setTransactionIsolation(level);
    } catch (SQLException exception) {
      throw new RuntimeException(exception);
    }
  }

  private void printConnectionDetails(Connection connection) {
    int isolationLevelIntegerValue;
    boolean autoCommit;
    try {
      isolationLevelIntegerValue = connection.getTransactionIsolation();
      autoCommit = connection.getAutoCommit();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    String isolationLevelStringValue = null;

    switch (isolationLevelIntegerValue) {
      case Connection.TRANSACTION_READ_UNCOMMITTED:
        isolationLevelStringValue = "READ_UNCOMMITTED";
        break;
      case Connection.TRANSACTION_READ_COMMITTED:
        isolationLevelStringValue = "READ_COMMITTED";
        break;
      case Connection.TRANSACTION_REPEATABLE_READ:
        isolationLevelStringValue = "REPEATABLE_READ";
        break;
      case Connection.TRANSACTION_SERIALIZABLE:
        isolationLevelStringValue = "SERIALIZABLE";
        break;
    }

    log.info("Connection: {}; transaction isolation level: {}; autocommit: {}", connection, isolationLevelStringValue, autoCommit);
  }
}
