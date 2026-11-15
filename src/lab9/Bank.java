package lab9;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class Bank {
    private final List<Account> accounts;
    private final Set<Account> members;

    public Bank(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            throw new IllegalArgumentException("At least one account is required");
        }
        this.members = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Integer> ids = new java.util.HashSet<>();
        for (Account account : accounts) {
            if (account == null || !ids.add(account.id()) || !members.add(account)) {
                throw new IllegalArgumentException("Account IDs and instances must be unique");
            }
        }
        this.accounts = List.copyOf(accounts);
    }

    public List<Account> accounts() {
        return accounts;
    }


    public boolean transfer(Account from, Account to, int amount) {
        if (from == null || to == null || !members.contains(from) || !members.contains(to)) {
            throw new IllegalArgumentException("Both accounts must belong to this bank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (from == to) {
            return false;
        }

        Account first = from.id() < to.id() ? from : to;
        Account second = from.id() < to.id() ? to : from;
        ReentrantLock lock1 = first.mutex();
        ReentrantLock lock2 = second.mutex();
        lock1.lock();
        try {
            lock2.lock();
            try {
                if (from.balanceLocked() < amount) {
                    return false;
                }
                // Overflow is impossible for a legal transfer because all balances are
                // nonnegative and the bank's fixed total already fits in a long.
                from.withdrawLocked(amount);
                to.depositLocked(amount);
                return true;
            } finally {
                lock2.unlock();
            }
        } finally {
            lock1.unlock();
        }
    }


    public long totalBalance() {
        long total = 0;
        for (Account account : accounts) {
            total = Math.addExact(total, account.balance());
        }
        return total;
    }

    public long minimumBalance() {
        long min = Long.MAX_VALUE;
        for (Account account : accounts) {
            min = Math.min(min, account.balance());
        }
        return min;
    }
}
