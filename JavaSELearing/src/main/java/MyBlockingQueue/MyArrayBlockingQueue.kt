package MyBlockingQueue

import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.measureNanoTime

/**
 * Created by haoyifen on 2017/3/12  18:26.
 */
class MyArrayBlockingQueue<T>(override val size: Int) : MyBlockingQueue.BlockingQueue<T> {

    val lock: ReentrantLock = ReentrantLock()
    val notEmpty = lock.newCondition()
    val notFull = lock.newCondition()
    val objects = arrayOfNulls<Any>(size)
    var putIndex = 0
    var takeIndex = 0
    var count = 0


    override fun put(e: T) {
        lock.withLock {
            while (count == objects.size) {
                notFull.await()
            }
            enqueue(e)
        }
    }

    private fun enqueue(e: T): Unit {
        logger.debug("enqueue $e @index $putIndex, count: $count")
        objects[putIndex] = e
        if (++putIndex == objects.size) {
            putIndex = 0
        }
        count++
        notEmpty.signal()
    }

    private fun dequeue(): T {
        @SuppressWarnings("unchecked")
        val value: T = objects [takeIndex] as T
        objects[takeIndex] = null
        logger.debug("dequeue $value @index $takeIndex, count: $count")
        if (++takeIndex == objects.size) {
            takeIndex = 0
        }
        count--
        notFull.signal()
        return value
    }

    override fun take(): T {
        lock.withLock {
            while (count == 0) {
                notEmpty.await()
            }
            return dequeue()
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(MyArrayBlockingQueue::class.java)
    }
}


class testMyArrayBlockigQueue {
    @Test
    fun insertConcurrency() {
        val queue = MyArrayBlockingQueue<String>(10)
        val block = {
            queue.put(Thread.currentThread().toString())
        }
        test(block, 10)
        val takeBlock ={
            queue.take()
        }
        test(takeBlock,11)
    }

}

fun test(block: () -> Any, task: Int = 10): Unit {
    val measureNanoTime = measureNanoTime {
        val threads = arrayOfNulls<Thread>(task)
        val countDownLatch = CountDownLatch(1)
        (task - 1).downTo(0).reversed().forEach {
            Thread {
                countDownLatch.await()
                block()
            }.apply {
                start()
                threads[it] = this
            }
        }
        countDownLatch.countDown()
        threads.forEach { it!!.join() }
    }
    println("used ${measureNanoTime / 1000 / 1000} ms")
}

fun withLock(lock: Lock, block: () -> Unit) {
    lock.lock()
    try {
        block()
    } finally {
        lock.unlock()
    }
}