package org.frameworkset.tran.file.monitor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

/**
 * Refactor from commons-io
 * @author xutengfei
 * @description
 * @create 2021/3/23
 */
public class FileAlterationMonitor  implements Runnable{
    private final long interval;
    private final List<FileAlterationObserver> observers = new CopyOnWriteArrayList<>();
    private Thread thread = null;
    private ThreadFactory threadFactory;
    private volatile boolean running = false;

    /**
     * Constructs a monitor with a default interval of 10 seconds.
     */
    public FileAlterationMonitor() {
        this(10000);
    }

    /**
     * Constructs a monitor with the specified interval.
     *
     * @param interval The amount of time in milliseconds to wait between
     * checks of the file system
     */
    public FileAlterationMonitor(final long interval) {
        this.interval = interval;
    }

    /**
     * Constructs a monitor with the specified interval and set of observers.
     *
     * @param interval The amount of time in milliseconds to wait between
     * checks of the file system
     * @param observers The set of observers to add to the monitor.
     */
    public FileAlterationMonitor(final long interval, FileAlterationObserver... observers) {
        this(interval);
        if (observers != null) {
            for (FileAlterationObserver observer : observers) {
                addObserver(observer);
            }
        }
    }

    /**
     * Returns the interval.
     *
     * @return the interval
     */
    public long getInterval() {
        return interval;
    }

    /**
     * Sets the thread factory.
     *
     * @param threadFactory the thread factory
     */
    public synchronized void setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
     * Adds a file system observer to this monitor.
     *
     * @param observer The file system observer to add
     */
    public void addObserver(FileAlterationObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    /**
     * Removes a file system observer from this monitor.
     *
     * @param observer The file system observer to remove
     */
    public void removeObserver(FileAlterationObserver observer) {
        if (observer != null) {
            while (observers.remove(observer)) {
                // empty
            }
        }
    }

    /**
     * Returns the set of {@link org.frameworkset.tran.file.monitor.FileAlterationObserver} registered with
     * this monitor.
     *
     * @return The set of {@link org.frameworkset.tran.file.monitor.FileAlterationObserver}
     */
    public Iterable<FileAlterationObserver> getObservers() {
        return observers;
    }

    /**
     * Starts monitoring.
     *
     * @throws Exception if an error occurs initializing the observer
     */
    public synchronized void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Monitor is already running");
        }
        for (FileAlterationObserver observer : observers) {
            observer.initialize();
        }
        running = true;
        if (threadFactory != null) {
            thread = threadFactory.newThread(this);
        } else {
            thread = new Thread(this,"LogFile-Change-monitor");
        }
        thread.start();
    }

    /**
     * Stops monitoring.
     *
     * @throws Exception if an error occurs initializing the observer
     */
    public synchronized void stop() throws Exception {
        stop(interval);
    }

    /**
     * Stops monitoring.
     *
     * @param stopInterval the amount of time in milliseconds to wait for the thread to finish.
     * A value of zero will wait until the thread is finished (see {@link Thread#join(long)}).
     * @throws Exception if an error occurs initializing the observer
     * @since 2.1
     */
    public synchronized void stop(final long stopInterval) throws Exception {
        if (running == false) {
            throw new IllegalStateException("Monitor is not running");
        }
        running = false;
        try {
            thread.interrupt();
            thread.join(stopInterval);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (FileAlterationObserver observer : observers) {
            observer.destroy();
        }
    }

    /**
     * Runs this monitor.
     */
    @Override
    public void run() {
        while (running) {
            for (final FileAlterationObserver observer : observers) {
                observer.checkAndNotify();
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(interval);
            } catch (final InterruptedException ignored) {
                // ignore
            }
        }
    }
}
