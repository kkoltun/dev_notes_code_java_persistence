package dev.karolkoltun.persistence.concurrency.optimistic.locking;

import dev.karolkoltun.persistence.concurrency.PostgresHrTest;
import dev.karolkoltun.persistence.concurrency.TwoThreads;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import dev.karolkoltun.persistence.entity.EmployeeVersioned;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import javax.persistence.OptimisticLockException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HibernateOptimisticLockingLongConversationTests extends PostgresHrTest {
    // There are four tests here, presenting two approaches to Long Conversations:
    // * Long Conversations implementation using Session-Per-Conversation pattern.
    // * Long Conversations implementation using Session-Per-Request pattern.

    @Test
    void longConversation_implementedWith_sessionPerConversationPattern_catchesConflict() {
        // This is an example, where a session is composed of multiple transactions.
        // Between the transactions, we give some time to the user to think, make decisions in the UI etc.
        // We cannot keep the transaction open in the think-time, because we don't know how much it will really take.
        // Having a transaction that is taking unlimited time would hurt scalability.

        // Thread 1 is the long conversation thread.
        // Thread 2 will try to ruin this mechanism by doing an update in the meantime.
        TwoThreadsWithTransactions.configure(entityManagerFactory, LongConversationContext::new)
                // The long conversation starts.
                .threadOneStartsWith(this::startLongConversationLoadEmployee)
                // Meanwhile another user saves a conflicting update.
                .thenThreadTwo(this::updateFirstEmployeeSalary)
                // The first user loads another objects - maybe they switched the page to edit another employee before saving.
                .thenThreadOne(this::loadAnotherEmployeeIntoConversation)
                .thenThreadTwoDoesNothing()
                // Finally the first user clicks save -> Hibernate should detect a conflicting change.
                .thenThreadOne(this::finishConversationAssertConflictIsCaught)
                .thenThreadTwoDoesNothing()
                .run();
    }

    @Test
    void longConversation_implementedWith_sessionPerConversationPattern_allowsUpdatesWhenNoConflict() {
        // This is basically the same test as before, but without any conflict.
        // The second user does an update, but in an unrelated employee.
        TwoThreadsWithTransactions.configure(entityManagerFactory, LongConversationContext::new)
                .threadOneStartsWith(this::startLongConversationLoadEmployee)
                .thenThreadTwo(this::updateUnrelatedEmployeeSalary)
                .thenThreadOne(this::loadAnotherEmployeeIntoConversation)
                .thenThreadTwoDoesNothing()
                .thenThreadOne(this::finishConversationNoConflictsHere)
                .thenThreadTwoDoesNothing()
                .run();
    }

    @Test
    void longConversation_implementedWith_sessionPerRequestPattern_catchesConflict() {
        // Thread 1 is the long conversation thread.
        // Thread 2 will try to ruin this mechanism by doing an update in the meantime.
        TwoThreads.configure(LongConversationContext::new)
                // The long conversation starts.
                .threadOneStartsWith(this::startLongConversationLoadEmployee_shortSession)
                // Meanwhile another user saves a conflicting update.
                .thenThreadTwo(this::updateFirstEmployeeSalary_shortSession)
                // The first user loads another objects - maybe they switched the page to edit another employee before saving.
                .thenThreadOne(this::loadAnotherEmployeeIntoConversation_shortSession)
                .thenThreadTwoDoesNothing()
                // Finally the first user clicks save -> Hibernate should detect a conflicting change.
                .thenThreadOne(this::finishConversationAssertConflictIsCaught_shortSession)
                .thenThreadTwoDoesNothing()
                .run();
    }

    @Test
    void longConversation_implementedWith_sessionPerRequestPattern_allowsUpdatesWhenNoConflict() {
        // This is basically the same test as before, but without any conflict.
        // The second user does an update, but in an unrelated employee.
        TwoThreads.configure(LongConversationContext::new)
                .threadOneStartsWith(this::startLongConversationLoadEmployee_shortSession)
                .thenThreadTwo(this::updateUnrelatedEmployeeSalary_shortSession)
                .thenThreadOne(this::loadAnotherEmployeeIntoConversation_shortSession)
                .thenThreadTwoDoesNothing()
                .thenThreadOne(this::finishConversationNoConflictsHere_shortSession)
                .thenThreadTwoDoesNothing()
                .run();
    }

    private void startLongConversationLoadEmployee_shortSession(LongConversationContext context) {
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {

            startLongConversationLoadEmployee(session, context);

            // This is the point where the request ends, so the session ends too.
        }
    }

    private void startLongConversationLoadEmployee(Session session, LongConversationContext context) {
        // This is the first step for Thread 1 in the tests.
        // If executed in a session context, the transaction will already be open.
        // If executed in a session-less context, we need to open the transaction ourselves.
        if (!session.getTransaction().isActive()) {
            session.beginTransaction();
        }

        // Do not flush until instructed! Saving changes will be the last step of the conversation.
        session.setHibernateFlushMode(FlushMode.MANUAL);

        // Get an employee object.
        EmployeeVersioned employee = session.find(EmployeeVersioned.class, 100);
        assertThat(employee).isNotNull();

        // The employee object is passed to the UI. The user will be working on it for quite a while...
        // The context represents the objects pulled from the DB, that are currently operated on.
        context.setFirstEmployee(employee);

        // The transaction ends, no saving.
        session.getTransaction().commit();
    }

    private void loadAnotherEmployeeIntoConversation_shortSession(LongConversationContext context) {
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            loadAnotherEmployeeIntoConversation(session, context);
        }
    }

    private void loadAnotherEmployeeIntoConversation(Session session, LongConversationContext context) {
        // The user selects another employee to edit.
        session.getTransaction().begin();
        EmployeeVersioned anotherEmployee = session.find(EmployeeVersioned.class, 101);
        session.getTransaction().commit();

        context.setSecondEmployee(anotherEmployee);
    }

    private void updateFirstEmployeeSalary_shortSession(LongConversationContext context) {
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            updateFirstEmployeeSalary(session, context);
        }
    }

    private void updateFirstEmployeeSalary(Session session, LongConversationContext context) {
        // This is the first step for Thread 2 in the conflict test.
        // If executed in a session context, the transaction will already be open.
        // If executed in a session-less context, we need to open the transaction ourselves.
        if (!session.getTransaction().isActive()) {
            session.beginTransaction();
        }

        EmployeeVersioned employee = session.find(EmployeeVersioned.class, 100);
        assertThat(employee).isNotNull();

        employee.setSalary(employee.getSalary().add(BigDecimal.TEN));
        session.getTransaction().commit();
    }

    private void updateUnrelatedEmployeeSalary_shortSession(LongConversationContext context) {
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            updateUnrelatedEmployeeSalary(session, context);
        }
    }

    private void updateUnrelatedEmployeeSalary(Session session, LongConversationContext context) {
        // This is the first step for Thread 2 in the no-conflict test.
        // If executed in a session context, the transaction will already be open.
        // If executed in a session-less context, we need to open the transaction ourselves.
        if (!session.getTransaction().isActive()) {
            session.beginTransaction();
        }

        EmployeeVersioned employee = session.find(EmployeeVersioned.class, 110);
        assertThat(employee).isNotNull();

        employee.setSalary(employee.getSalary().add(BigDecimal.TEN));
        session.getTransaction().commit();
    }

    private void finishConversationAssertConflictIsCaught_shortSession(LongConversationContext context) {
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            // We need to first open session and re-attach objects, then we can try to save the changes done by the user.
            // Hibernate should still detect conflict.
            reattachObjectsToSession(context, session);

            // Now we can proceed with the ordinary test
            finishConversationAssertConflictIsCaught(session, context);
        }
    }

    private void finishConversationAssertConflictIsCaught(Session session, LongConversationContext context) {
        // User clicked save, finally!
        EmployeeVersioned firstEmployee = context.getFirstEmployee();
        EmployeeVersioned secondEmployee = context.getSecondEmployee();

        // These are the changes they want to save:
        firstEmployee.setEmail("upd_" + firstEmployee.getEmail());
        secondEmployee.setSalary(secondEmployee.getSalary().add(BigDecimal.TEN));

        // First check if the session is still holding the edited objects.
        assertThat(session.contains(firstEmployee)).isTrue();
        assertThat(session.contains(secondEmployee)).isTrue();

        // Now open the transaction and flush changes.
        session.getTransaction().begin();

        // Hibernate should detect that there was a conflicting change in the entity (first employee, change done by thread 2).
        // Note that without Hibernate versioning, there is no way for the database to see the conflict - the transaction has just started!
        assertThatThrownBy(session::flush).isInstanceOf(OptimisticLockException.class);
    }

    private void finishConversationNoConflictsHere_shortSession(LongConversationContext context) {
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            // We need to first open session and re-attach objects, then we can try to save the changes done by the user.
            // Hibernate should still detect conflict.
            reattachObjectsToSession(context, session);

            finishConversationNoConflictsHere(session, context);
        }
    }

    private void finishConversationNoConflictsHere(Session session, LongConversationContext context) {
        // User clicked save, finally!
        EmployeeVersioned firstEmployee = context.getFirstEmployee();
        EmployeeVersioned secondEmployee = context.getSecondEmployee();

        // These are the changes they want to save:
        firstEmployee.setEmail("upd_" + firstEmployee.getEmail());
        secondEmployee.setSalary(secondEmployee.getSalary().add(BigDecimal.TEN));

        // First check if the session is still holding the edited objects.
        assertThat(session.contains(firstEmployee)).isTrue();
        assertThat(session.contains(secondEmployee)).isTrue();

        // Now open the transaction and flush changes.
        session.getTransaction().begin();
        session.flush();
        session.getTransaction().commit();
    }

    private static void reattachObjectsToSession(LongConversationContext context, Session session) {
        EmployeeVersioned firstEmployee = context.getFirstEmployee();
        EmployeeVersioned secondEmployee = context.getSecondEmployee();

        assertThat(session.contains(firstEmployee)).isFalse();
        assertThat(session.contains(secondEmployee)).isFalse();

        // This is a re-attach according to Java Persistence with Hibernate. A fragment from the book, section 9.3.2:
            /*
              A detached instance may be reattached to a new Session (and managed by this
              new persistence context) by calling update() on the detached object. In our
              experience, it may be easier for you to understand the following code if you
              rename the update() method in your mind to reattach()—however, there is a
              good reason it’s called updating.
             */
        session.update(context.getFirstEmployee());
        session.update(context.getSecondEmployee());

        assertThat(session.contains(firstEmployee)).isTrue();
        assertThat(session.contains(secondEmployee)).isTrue();
    }

    private static class LongConversationContext {
        private EmployeeVersioned firstEmployee;
        private EmployeeVersioned secondEmployee;

        public EmployeeVersioned getFirstEmployee() {
            return firstEmployee;
        }

        public void setFirstEmployee(EmployeeVersioned firstEmployee) {
            this.firstEmployee = firstEmployee;
        }

        public EmployeeVersioned getSecondEmployee() {
            return secondEmployee;
        }

        public void setSecondEmployee(EmployeeVersioned secondEmployee) {
            this.secondEmployee = secondEmployee;
        }
    }
}
