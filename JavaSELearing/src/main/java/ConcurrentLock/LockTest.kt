package ConcurrentLock

import java.util.concurrent.locks.ReentrantLock

/**
 * Created by haoyifen on 2017/3/10  18:15.
 */

fun main(args: Array<String>) {
    val lock: ReentrantLock = ReentrantLock(true)
    val sum = 100
    val threads = arrayOfNulls<Thread>(sum)
    for (i in 0..sum-1) {
        Thread {
            lock.lock()
            try {
                println(lock)
                Thread.sleep(100000)
            } finally {
                lock.unlock()
            }
        }.apply {
            threads[i] = this
            start()
        }
    }
    threads.forEach { it?.join() }
}