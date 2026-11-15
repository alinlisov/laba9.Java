package lab9;

import java.util.concurrent.locks.ReentrantLock;

public final class Account {
    private final int id;
    private long balance;
    private final ReentrantLock lock = new ReentrantLock();

    public Account(int id, long openingBalance) {
        if (id <= 0 || openingBalance < 0) {
            throw new IllegalArgumentException("Invalid account ID or opening balance");
        }
        this.id = id;
        this.balance = openingBalance;
    }

    public int id() {
        return id;
    }


    public long balance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }


    ReentrantLock mutex() {
        return lock;
    }

    long balanceLocked() {
        return balance;
    }

    void withdrawLocked(long amount) {
        balance -= amount;
    }

    void depositLocked(long amount) {
        balance = Math.addExact(balance, amount);
    }
}
