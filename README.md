Repository with some code practicing various aspects of Java Persistence.

See [here](https://kkoltun.github.io/dev_notes/#/java_persistence/README) for dev_notes on the Java Persistence topic.

TODO test the following mechanisms:
1. Pessimistic locking:
    1. `SKIP_LOCKED` mechanism.
    2. PostgreSQL advisory lock tests.
2. Optimistic locking:
   1. Versionless optimistic locking applied by using `@DynamicUpdate` with `@OptimisticLocking(type = OptimisticLockType.DIRTY)`.
   2. `LockModeType` values.
   3. Implement and test the mechanism [described here](https://vladmihalcea.com/how-to-increment-the-parent-entity-version-whenever-a-child-entity-gets-modified-with-jpa-and-hibernate/).
3. Add more isolation level, isolation issue tests (official issues + MVCC issues).