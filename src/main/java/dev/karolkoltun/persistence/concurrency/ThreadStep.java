package dev.karolkoltun.persistence.concurrency;

public class ThreadStep<TASK> {
    private final Runnable fireNextStepRunnable;

    private final Runnable blockUntilFiredRunnable;
    private final int stepCount;
    private final TASK task;

    public ThreadStep(Runnable fireNextStepRunnable, Runnable blockUntilFiredRunnable, int stepCount, TASK task) {
        this.fireNextStepRunnable = fireNextStepRunnable;
        this.blockUntilFiredRunnable = blockUntilFiredRunnable;
        this.stepCount = stepCount;
        this.task = task;
    }

    public void fireNextStepOnAnotherThread() {
        fireNextStepRunnable.run();
    }

    public void blockUntilFiredByAnotherThread() {
        blockUntilFiredRunnable.run();
    }

    public int getStepIndex() {
        return stepCount;
    }

    public TASK getTask() {
        return task;
    }
}
