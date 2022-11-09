package dev.karolkoltun.persistence.concurrency.optimistic.locking;

import dev.karolkoltun.persistence.concurrency.PostgresHrTest;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import dev.karolkoltun.persistence.entity.EmployeeVersioned;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import javax.persistence.OptimisticLockException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

public class HibernateOptimisticLockingLongConversationTests extends PostgresHrTest {
    @Test
    void hibernateVersioningAllowsLongConversations_spanningMultipleTransactions_willCatchAnyConflicts() {
        // This is an example, where a session is composed of multiple transactions.
        // Between the transactions, we give some time to the user to think, make decisions in the UI etc.
        // We cannot keep the transaction open in the think-time, because we don't know how much it will really take.
        // Having a transaction that is taking unlimited time would hurt scalability.

        // Thread 1 is the long conversation thread.
        // Thread 2 will try to ruin this mechanism by doing an update in the meantime.
        TwoThreadsWithTransactions.configure(entityManagerFactory, LongConversationContext::new)
                // The long conversation starts.
                .threadOneStartsWith(HibernateOptimisticLockingLongConversationTests::startLongConversationLoadEmployee)
                // Meanwhile another user saves a conflicting update.
                .thenThreadTwo(HibernateOptimisticLockingLongConversationTests::updateFirstEmployeeSalary)
                // The first user loads another objects - maybe they switched the page to edit another employee before saving.
                .thenThreadOne(HibernateOptimisticLockingLongConversationTests::loadAnotherEmployeeIntoConversation)
                .thenThreadTwoDoesNothing()
                // Finally the first user clicks save -> Hibernate should detect a conflicting change.
                .thenThreadOne(HibernateOptimisticLockingLongConversationTests::finishConversationAssertConflictIsCaught)
                .thenThreadTwoDoesNothing()
                .run();
    }

    private static void startLongConversationLoadEmployee(Session session, LongConversationContext context) {
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

    private static void loadAnotherEmployeeIntoConversation(Session session, LongConversationContext context) {
        // The user selects another employee to edit.
        session.getTransaction().begin();
        EmployeeVersioned anotherEmployee = session.find(EmployeeVersioned.class, 101);
        session.getTransaction().commit();

        context.setSecondEmployee(anotherEmployee);
    }

    private static void finishConversationAssertConflictIsCaught(Session session, LongConversationContext context) {
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


    private static void finishConversationNoConflictsHere(Session session, LongConversationContext context) {
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


    private static void updateFirstEmployeeSalary(Session session, LongConversationContext context) {
        EmployeeVersioned employee = session.find(EmployeeVersioned.class, 100);
        assertThat(employee).isNotNull();

        employee.setSalary(employee.getSalary().add(BigDecimal.TEN));
        session.getTransaction().commit();
    }

    private static void updateUnrelatedEmployeeSalary(Session session, LongConversationContext context) {
        EmployeeVersioned employee = session.find(EmployeeVersioned.class, 110);
        assertThat(employee).isNotNull();

        employee.setSalary(employee.getSalary().add(BigDecimal.TEN));
        session.getTransaction().commit();
    }

    @Test
    void hibernateVersioningAllowsLongConversations_spanningMultipleTransactions_willWorkWhenThereAreNoConflicts() {
        // This is basically the same test as before, but without any conflict.
        // The second user does an update, but in an unrelated employee.
        TwoThreadsWithTransactions.configure(entityManagerFactory, LongConversationContext::new)
                .threadOneStartsWith(HibernateOptimisticLockingLongConversationTests::startLongConversationLoadEmployee)
                .thenThreadTwo(HibernateOptimisticLockingLongConversationTests::updateUnrelatedEmployeeSalary)
                .thenThreadOne(HibernateOptimisticLockingLongConversationTests::loadAnotherEmployeeIntoConversation)
                .thenThreadTwoDoesNothing()
                .thenThreadOne(HibernateOptimisticLockingLongConversationTests::finishConversationNoConflictsHere)
                .thenThreadTwoDoesNothing()
                .run();
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
