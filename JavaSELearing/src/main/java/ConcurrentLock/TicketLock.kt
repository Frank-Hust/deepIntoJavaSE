package ConcurrentLock

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.getOrSet
import kotlin.system.measureNanoTime

/**
 * Created by haoyifen on 2017/3/10  15:23.
 * Ticket Lock 是为了解决上面的公平性问题，类似于现实中银行柜台的排队叫号：锁拥有一个服务号，表示正在服务的线程，还有一个排队号；每个线程尝试获取锁之前先拿一个排队号，然后不断轮询锁的当前服务号是否是自己的排队号，如果是，则表示自己拥有了锁，不是则继续轮询。

当线程释放锁时，将服务号加1，这样下一个线程看到这个变化，就退出自旋。
 */
class TicketLock : Lock {
    companion object {
        val logger = LoggerFactory.getLogger(TicketLock::class.java)
    }

    val triedTimesThreadLocal: ThreadLocal<AtomicInteger> = ThreadLocal()
    val myTicket = ThreadLocal<Int>()
    val serviceNum = AtomicInteger(0)
    val ticketNum = AtomicInteger(0)
    override fun lock() {
        myTicket.set(ticketNum.getAndIncrement())
        val triedTimes = triedTimesThreadLocal.getOrSet { AtomicInteger(0) }
        while (serviceNum.get() != myTicket.get()) {
            triedTimes.incrementAndGet()
            Thread.sleep(10)
        }
    }

    override fun unlock() {
        val next = myTicket.get() + 1
        serviceNum.compareAndSet(myTicket.get(), next)
    }

}

fun main(args: Array<String>) {
    var getLockTickets = ConcurrentLinkedQueue<Int>()
    var count: Int = 0
    val measureNanoTime = measureNanoTime {
        val task = 100
        val threads = arrayOfNulls<Thread>(100)
        val countDownLatch = CountDownLatch(1)
        val spinLock = TicketLock()
        val concurrentHashMap = ConcurrentHashMap<Thread, AtomicInteger>()
        (task - 1).downTo(0).reversed().forEach {
            Thread {
                countDownLatch.await()
                spinLock.lock()
                try {
                    val myTicket = spinLock.myTicket.get()
                    getLockTickets.add(myTicket)
                    Thread.sleep(20)
                } finally {
                    spinLock.unlock()
                    concurrentHashMap[Thread.currentThread()] = spinLock.triedTimesThreadLocal.get()
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
    var lastOne=getLockTickets.indexOf(0)
    val allMatch = getLockTickets.stream().skip(1).allMatch {
        val lastOne1 = lastOne
        lastOne = it
        it >= lastOne1
    }
    println(allMatch)
}