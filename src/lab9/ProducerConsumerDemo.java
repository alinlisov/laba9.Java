package lab9;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class ProducerConsumerDemo {
    public static final int PRODUCERS = 5;
    public static final int TRANSLATORS = 2;
    public static final int REQUIRED_MESSAGES = 100;
    public static final int CAPACITY = 8;

    private static final Pattern TRANSLATED_ID =
            Pattern.compile("^Потік № [67] переклав повідомлення (\\d+) \\(джерело: .+\\)$");

    public record Result(int producers, int translators, int capacity,
                         int produced, int translated, int printed, int unique,
                         boolean allWorkersWereDaemons) {
        public boolean passed() {
            return producers == PRODUCERS && translators == TRANSLATORS
                    && produced == REQUIRED_MESSAGES && translated == REQUIRED_MESSAGES
                    && printed == REQUIRED_MESSAGES && unique == REQUIRED_MESSAGES
                    && allWorkersWereDaemons;
        }
    }

    private ProducerConsumerDemo() { }

    public static Result run(boolean printMessages) throws InterruptedException {
        RingBuffer<String> first = new RingBuffer<>(CAPACITY);
        RingBuffer<String> second = new RingBuffer<>(CAPACITY);
        AtomicInteger nextId = new AtomicInteger(1);
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger translated = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();

        for (int i = 1; i <= PRODUCERS; i++) {
            final int producerId = i;
            Thread worker = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        int id = nextId.getAndIncrement();
                        if (id > REQUIRED_MESSAGES) {
                            break;
                        }
                        first.put("Потік № " + producerId + " згенерував повідомлення " + id);
                        produced.incrementAndGet();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Throwable unexpected) {
                    error.compareAndSet(null, unexpected);
                }
            }, "Producer-" + producerId);
            worker.setDaemon(true);
            workers.add(worker);
        }

        for (int i = 1; i <= TRANSLATORS; i++) {
            final int translatorId = PRODUCERS + i; // IDs 6 and 7
            Thread worker = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String source = first.take();
                        int lastSpace = source.lastIndexOf(' ');
                        String messageId = source.substring(lastSpace + 1);
                        String translatedMessage = "Потік № " + translatorId
                                + " переклав повідомлення " + messageId
                                + " (джерело: " + source + ")";
                        second.put(translatedMessage);
                        translated.incrementAndGet();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Throwable unexpected) {
                    error.compareAndSet(null, unexpected);
                }
            }, "Translator-" + translatorId);
            worker.setDaemon(true);
            workers.add(worker);
        }

        boolean allDaemon = workers.stream().allMatch(Thread::isDaemon);
        for (Thread worker : workers) {
            worker.start();
        }

        Set<Integer> ids = new HashSet<>();
        int printed = 0;
        try {
            if (printMessages) {
                System.out.println("=== ЗАВДАННЯ 2: ВИРОБНИКИ / СПОЖИВАЧІ ===");
                System.out.println("Виробників-демонів: " + PRODUCERS + ", перекладачів-демонів: " + TRANSLATORS);
                System.out.println("Два кільцеві буфери, місткість кожного: " + CAPACITY);
            }
            for (int i = 0; i < REQUIRED_MESSAGES; i++) {
                String message = second.take();
                Matcher matcher = TRANSLATED_ID.matcher(message);
                if (!matcher.matches()) {
                    throw new AssertionError("Unexpected translated message: " + message);
                }
                int id = Integer.parseInt(matcher.group(1));
                if (id < 1 || id > REQUIRED_MESSAGES || !ids.add(id)) {
                    throw new AssertionError("Repeated or invalid message ID: " + id);
                }
                ++printed;
                if (printMessages) {
                    System.out.printf("[%03d/%d] %s%n", printed, REQUIRED_MESSAGES, message);
                }
            }
        } finally {
            // All 100 items have been consumed, or the main thread is leaving on error.
            for (Thread worker : workers) {
                worker.interrupt();
            }
            for (Thread worker : workers) {
                worker.join(5_000);
            }
        }
        if (error.get() != null) {
            throw new IllegalStateException("Error in a worker thread", error.get());
        }
        Result result = new Result(PRODUCERS, TRANSLATORS, CAPACITY,
                produced.get(), translated.get(), printed, ids.size(), allDaemon);
        if (printMessages) {
            System.out.println("Згенеровано: " + result.produced());
            System.out.println("Перекладено: " + result.translated());
            System.out.println("Надруковано основним потоком: " + result.printed());
            System.out.println("Унікальних номерів повідомлень: " + result.unique());
            System.out.println("Усі 7 робочих потоків — демони: " + result.allWorkersWereDaemons());
            System.out.println("Результат перевірки: " + (result.passed() ? "УСПІШНО" : "ПОМИЛКА"));
            System.out.println();
        }
        if (!result.passed()) {
            throw new AssertionError("Producer-consumer invariant failed: " + result);
        }
        return result;
    }
}
