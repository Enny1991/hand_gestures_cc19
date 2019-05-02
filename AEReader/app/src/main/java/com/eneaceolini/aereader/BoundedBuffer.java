package com.eneaceolini.aereader;

class BoundedBuffer<T> {
    private volatile boolean closed = false;

    final Object[] items = new Object[1000];
    int putptr, takeptr, count;

    public synchronized void put(T x) throws InterruptedException {
        while (count == items.length)
            wait();
        items[putptr] = x;
        if (++putptr == items.length) putptr = 0;
        ++count;
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (count == 0)
            wait();
        Object x = items[takeptr];
        if (++takeptr == items.length) takeptr = 0;
        --count;
        notifyAll();
        return (T) x;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }
}