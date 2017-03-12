package ConcurrentLock

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.getOrSet
import kotlin.system.measureNanoTime

/**
 * Created by haoyifen on 2017/3/10  13:15.
 * 自旋锁, 没有获取到锁的话, 就不停的进行判断, 只适合临界区很小, 锁竞争不大的情况
 * */
class SpinLock : Lock {
    companion object {
        val logger = LoggerFactory.getLogger(SpinLock::class.java)
    }

    private val lockOwner = AtomicReference<Thread>()
    val triedTimesThreadLocal: ThreadLocal<AtomicInteger> = ThreadLocal()

    override fun lock() {
        val currentThread = Thread.currentThread()
        logger.debug("try to get lock {}", this)
        val triedTimes = triedTimesThreadLocal.getOrSet { AtomicInteger(0) }
        while (!lockOwner.compareAndSet(null, currentThread)) {
            triedTimes.incrementAndGet()
            logger.debug("get lock failed, tried $triedTimes ,go to sleep 10ms")
            Thread.sleep(10)
        }
        logger.info("success  get lock {}", this)
    }

    override fun unlock() {
        val currentThread = Thread.currentThread()
//        logger.debug("try to unlock lock $this")
        val lockOwnerThread = lockOwner.get()
        if (currentThread != lockOwnerThread) {
            logger.debug("not the owner of lock $this, but the $lockOwnerThread")
            throw IllegalArgumentException("not owner of the lock")
        }
        val unlock = lockOwner.compareAndSet(currentThread, null)
        if (unlock) {
            logger.info("unlock lock $this success")
        }
    }

}


fun main(args: Array<String>) {
    var count: Int = 0
    val measureNanoTime = measureNanoTime {
        val task = 100
        val threads = arrayOfNulls<Thread>(100)
        val countDownLatch = CountDownLatch(1)
        val spinLock = SpinLock()
        val concurrentHashMap = ConcurrentHashMap<Thread, AtomicInteger>()
        (task - 1).downTo(0).reversed().forEach {
            Thread {
                countDownLatch.await()
                spinLock.lock()
                try {
                    Thread.sleep(20)
                } finally {
                    spinLock.unlock()
                    concurrentHashMap[Thread.currentThread()] = spinLock.triedTimesThreadLocal.getOrSet { AtomicInteger(0) }
                }
            }.apply {
                start()
                threads[it] = this
            }
        }
        countDownLatch.countDown()
        threads.forEach { it!!.join() }
        count = concurrentHashMap.values.map { it.get() }.sum()

    }
    println("used ${measureNanoTime / 1000 / 1000} ms, total spin $count")
}