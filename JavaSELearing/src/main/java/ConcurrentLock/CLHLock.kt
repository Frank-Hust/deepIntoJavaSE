package ConcurrentLock

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.concurrent.getOrSet
import kotlin.system.measureNanoTime


/**
 * Created by haoyifen on 2017/3/10  16:57.
 */
class CLHLock : Lock {
    class CLHNode {
        @Volatile var isLocked = true // 默认是在等待锁
    }

    @Volatile private var tail: CLHNode? = null
    val nodeThreadLocal = ThreadLocal<CLHNode>()
    override fun lock() {
        val currentThreadCLHNode: CLHNode = nodeThreadLocal.getOrSet { CLHNode() }
        val preNode = UPDATER.getAndSet(this, currentThreadCLHNode) // 把this里的"tail" 值设置成currentThreadCLHNode
        if (preNode != null) {//已有线程占用了锁，进入自旋
            while (preNode.isLocked) {
                Thread.sleep(10)
            }
        }
        logger.info("success get  {}", this)
    }

    override fun unlock() {
        val currentThreadCLHNode = nodeThreadLocal.getOrSet { CLHNode() }
        // 如果队列里只有当前线程，则释放对当前线程的引用（for GC）。
        if (!UPDATER.compareAndSet(this, currentThreadCLHNode, null)) {
            // 还有后续线程
            currentThreadCLHNode.isLocked = false// 改变状态，让后续线程结束自旋
        }
        logger.info("success unlock  {}", this)
    }

    companion object {
        private val UPDATER = AtomicReferenceFieldUpdater.newUpdater(CLHLock::class.java, CLHNode::class.java, "tail")
        val logger = LoggerFactory.getLogger(CLHLock::class.java)
    }
}

fun main(args: Array<String>) {
    val measureNanoTime = measureNanoTime {
        val task = 100
        val threads = arrayOfNulls<Thread>(100)
        val countDownLatch = CountDownLatch(1)
        val lock = CLHLock()
        (task - 1).downTo(0).reversed().forEach {
            Thread {
                countDownLatch.await()
                lock.lock()
                try {
                    Thread.sleep(20)
                } finally {
                    lock.unlock()
                }
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