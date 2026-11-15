package lab9;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public final class RingBuffer<T> {
    private static final class Node<E> {
        E value;
        Node<E> next;
    }

    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private Node<T> head;
    private Node<T> tail;
    private int size;

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Buffer capacity must be positive");
        }
        this.capacity = capacity;
        Node<T> first = new Node<>();
        Node<T> previous = first;
        for (int i = 1; i <= capacity; i++) {
            Node<T> next = new Node<>();
            previous.next = next;
            previous = next;
        }
        previous.next = first;
        head = first;
        tail = first;
    }

    public void put(T value) throws InterruptedException {
        if (value == null) {
            throw new IllegalArgumentException("Null values are not supported");
        }
        lock.lockInterruptibly();
        try {
            while (tail.next == head) {
                notFull.await();
            }
            tail.value = value;
            tail = tail.next;
            size++;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (head == tail) {
                notEmpty.await();
            }
            T result = head.value;
            head.value = null;
            head = head.next;
            size--;
            notFull.signal();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return capacity;
    }
}
