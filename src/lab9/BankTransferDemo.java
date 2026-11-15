package lab9;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public final class BankTransferDemo {
    public static final int ACCOUNT_COUNT = 200;
    public static final int TRANSFER_THREADS = 3_000;

    public record Result(int accounts, int threads, long before, long after,
                         long successful, long rejected, long minimumBalance,
                         long elapsedMillis) {
        public boolean passed() {
            return before == after && minimumBalance >= 0
                    && successful + rejected == threads;
        }
    }

    private BankTransferDemo() { }

    public static Result run(boolean verbose) throws Exception {
        SplittableRandom random = new SplittableRandom(2026);
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= ACCOUNT_COUNT; i++) {
            accounts.add(new Account(i, random.nextLong(1_000, 10_001)));
        }
        Bank bank = new Bank(accounts);
        long before = bank.totalBalance();
        CountDownLatch ready = new CountDownLatch(TRANSFER_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        LongAdder successful = new LongAdder();
        LongAdder rejected = new LongAdder();
        List<Future<?>> futures = new ArrayList<>();

        long startedAt = System.nanoTime();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TRANSFER_THREADS; i++) {
                Account from = accounts.get(random.nextInt(accounts.size()));
                Account to = accounts.get(random.nextInt(accounts.size()));
                int amount = random.nextInt(1, 10_001);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (from == to) {
                        rejected.increment();
                    } else if (bank.transfer(from, to, amount)) {
                        successful.increment();
                    } else {
                        rejected.increment();
                    }
                    return null;
                }));
            }
            try {
                if (!ready.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Not all virtual threads were ready");
                }
            } finally {
                start.countDown();
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        Result result = new Result(ACCOUNT_COUNT, TRANSFER_THREADS, before, bank.totalBalance(),
                successful.sum(), rejected.sum(), bank.minimumBalance(), elapsed);
        if (verbose) {
            System.out.println("=== ЗАВДАННЯ 1: БАНКІВСЬКІ ПЕРЕКАЗИ ===");
            System.out.println("Рахунків: " + result.accounts());
            System.out.println("Одночасно запущених віртуальних потоків: " + result.threads());
            System.out.println("Сума до переказів: " + result.before());
            System.out.println("Успішних переказів: " + result.successful());
            System.out.println("Відхилених (нестача коштів / той самий рахунок): " + result.rejected());
            System.out.println("Сума після переказів: " + result.after());
            System.out.println("Мінімальний залишок: " + result.minimumBalance());
            System.out.println("Час: " + result.elapsedMillis() + " мс");
            System.out.println("Результат перевірки: " + (result.passed() ? "УСПІШНО" : "ПОМИЛКА"));
            System.out.println();
        }
        if (!result.passed()) {
            throw new AssertionError("Bank invariant failed: " + result);
        }
        return result;
    }
}
