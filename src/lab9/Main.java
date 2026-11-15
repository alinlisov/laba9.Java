package lab9;

import java.util.Scanner;

public final class Main {
    private Main() { }

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("Лабораторна №9. Concurrency / Producer-Consumer");
                System.out.println("1 - Банківські перекази (3000 потоків)");
                System.out.println("2 - Кільцеві буфери (100 повідомлень)");
                System.out.println("3 - Обидва завдання");
                System.out.println("0 - Вихід");
                System.out.print("Оберіть пункт: ");
                String answer = scanner.nextLine().trim();
                try {
                    switch (answer) {
                        case "1" -> BankTransferDemo.run(true);
                        case "2" -> ProducerConsumerDemo.run(true);
                        case "3" -> {
                            BankTransferDemo.run(true);
                            ProducerConsumerDemo.run(true);
                        }
                        case "0" -> {
                            return;
                        }
                        default -> System.out.println("Оберіть 0, 1, 2 або 3.");
                    }
                } catch (Exception exception) {
                    System.err.println("Помилка виконання: " + exception.getMessage());
                    throw exception;
                }
            }
        }
    }
}
