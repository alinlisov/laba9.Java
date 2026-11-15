package lab9;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VerificationTests {
    private static int passed;

    private VerificationTests() { }

    public static void main(String[] args) throws Exception {
        check("bank: successful transfer", () -> {
            Account a = new Account(1, 100);
            Account b = new Account(2, 40);
            Bank bank = new Bank(List.of(a, b));
            require(bank.transfer(a, b, 70) && a.balance() == 30 && b.balance() == 110);
        });
        check("bank: reject insufficient funds", () -> {
            Account a = new Account(1, 5);
            Account b = new Account(2, 0);
            Bank bank = new Bank(List.of(a, b));
            require(!bank.transfer(a, b, 6) && a.balance() == 5 && b.balance() == 0);
        });
        check("bank: reject same account", () -> {
            Account a = new Account(1, 20);
            Bank bank = new Bank(List.of(a));
            require(!bank.transfer(a, a, 10) && bank.totalBalance() == 20);
        });
        check("bank: reject nonpositive amount", () -> {
            Account a = new Account(1, 20);
            Account b = new Account(2, 20);
            Bank bank = new Bank(List.of(a, b));
            boolean thrown = false;
            try { bank.transfer(a, b, -1); } catch (IllegalArgumentException expected) { thrown = true; }
            require(thrown && a.balance() == 20 && b.balance() == 20);
        });
        check("bank: reject a foreign account", () -> {
            Account a = new Account(1, 20);
            Account b = new Account(2, 10);
            Bank bank = new Bank(List.of(a));
            boolean thrown = false;
            try { bank.transfer(a, b, 1); } catch (IllegalArgumentException expected) { thrown = true; }
            require(thrown && a.balance() == 20 && b.balance() == 10);
        });
        check("bank: opposite transfers without deadlock", () -> {
            Account a = new Account(1, 1000);
            Account b = new Account(2, 1000);
            Bank bank = new Bank(List.of(a, b));
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> first = executor.submit(() -> {
                    for (int i = 0; i < 2000; i++) bank.transfer(a, b, 1);
                });
                Future<?> second = executor.submit(() -> {
                    for (int i = 0; i < 2000; i++) bank.transfer(b, a, 1);
                });
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            }
            require(bank.totalBalance() == 2000 && bank.minimumBalance() >= 0);
        });
        check("bank: 3000 virtual transfer threads", () -> {
            BankTransferDemo.Result r = BankTransferDemo.run(false);
            require(r.passed() && r.threads() == 3000 && r.accounts() == 200);
        });
        check("ring: first-in first-out", () -> {
            RingBuffer<Integer> ring = new RingBuffer<>(3);
            ring.put(10); ring.put(20);
            require(ring.take() == 10 && ring.take() == 20 && ring.size() == 0);
        });
        check("ring: wrap-around and capacity", () -> {
            RingBuffer<String> ring = new RingBuffer<>(2);
            ring.put("A"); ring.put("B");
            require(ring.size() == 2 && ring.take().equals("A"));
            ring.put("C");
            require(ring.take().equals("B") && ring.take().equals("C") && ring.size() == 0);
        });
        check("ring: consumer waits when empty", () -> {
            RingBuffer<String> ring = new RingBuffer<>(1);
            CountDownLatch entered = new CountDownLatch(1);
            AtomicBoolean done = new AtomicBoolean();
            Thread worker = new Thread(() -> {
                try {
                    entered.countDown(); ring.take(); done.set(true);
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            worker.start();
            require(entered.await(2, TimeUnit.SECONDS));
            Thread.sleep(80);
            require(!done.get());
            ring.put("go");
            worker.join(2000);
            require(!worker.isAlive() && done.get());
        });
        check("ring: producer waits when full", () -> {
            RingBuffer<String> ring = new RingBuffer<>(1);
            ring.put("first");
            CountDownLatch entered = new CountDownLatch(1);
            AtomicBoolean done = new AtomicBoolean();
            Thread worker = new Thread(() -> {
                try {
                    entered.countDown(); ring.put("second"); done.set(true);
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            worker.start();
            require(entered.await(2, TimeUnit.SECONDS));
            Thread.sleep(80);
            require(!done.get() && ring.size() == 1);
            require(ring.take().equals("first"));
            worker.join(2000);
            require(!worker.isAlive() && done.get() && ring.take().equals("second"));
        });
        check("ring: invalid capacity", () -> {
            boolean thrown = false;
            try { new RingBuffer<>(0); } catch (IllegalArgumentException expected) { thrown = true; }
            require(thrown);
        });
        check("producer-consumer: 5+2 daemons, exactly 100 unique", () -> {
            ProducerConsumerDemo.Result r = ProducerConsumerDemo.run(false);
            require(r.passed() && r.allWorkersWereDaemons());
        });
        System.out.println("УСПІШНО: " + passed + " тестів із " + passed);
    }

    private static void check(String name, Case operation) throws Exception {
        operation.run();
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Assertion failed");
    }

    @FunctionalInterface
    private interface Case {
        void run() throws Exception;
    }
}
