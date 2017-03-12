package ConcurrentLock

import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.getOrSet
import kotlin.system.measureNanoTime

/**
 * Created by haoyifen on 2017/3/10  17:26.
 *
 */

class CLHLock_1 : Lock {
    class CLHNode {
        @Volatile var status = 0 // 默认初始化
        @Volatile var prev: CLHNode? = null
        @Volatile var next: CLHNode? = null
        @Volatile var thread: Thread? = null

        companion object {
            val lockOwner = 1
            val initial = 0
            val blocked = -1
        }

        override fun toString(): String {
            return "CLHNode(status=$status, thread=${thread?.toString()})"
        }
    }

    @Volatile private var tail: CLHNode? = null
    @Volatile private var head: CLHNode? = null
    val nodeThreadLocal = ThreadLocal<CLHNode>()
    override fun lock() {
        enq()
        val currentNode = nodeThreadLocal.get()
        while (true) {
            val prev = currentNode.prev
            if (prev == null) {
                currentNode.status = CLHNode.lockOwner
                logger.info("success get lock")
                return
            } else if (prev == head && prev.status == CLHNode.initial) {
                head = currentNode
                prev.next = null
                currentNode.status = CLHNode.lockOwner
                logger.info("success get  lock")
                return
            }
            logger.debug("currentNode {} head {}", currentNode, head)
            LockSupport.park(this)
        }


    }

    private fun enq() {
        val currentThreadCLHNode: CLHNode = nodeThreadLocal.getOrSet { CLHNode() }
        currentThreadCLHNode.thread = Thread.currentThread()
        currentThreadCLHNode.status = CLHNode.blocked
        while (true) {
            val t: CLHNode? = tail
            if (t == null) {
                if (tailUpdater.compareAndSet(this, null, currentThreadCLHNode)) {
                    head = tail
                    return
                }
            } else {
                if (tailUpdater.compareAndSet(this, t, currentThreadCLHNode)) {
                    t.next = currentThreadCLHNode
                    currentThreadCLHNode.prev = t
                    return
                }
            }
        }
    }

    override fun unlock() {
        val currentThreadCLHNode = nodeThreadLocal.get()
        currentThreadCLHNode.status = CLHNode.initial
        val thread = currentThreadCLHNode?.next?.thread
        LockSupport.unpark(thread)
        logger.info("success unlock , currentNode {}, next node {}", currentThreadCLHNode, thread)

    }

    companion object {

        private val headUpdater = AtomicReferenceFieldUpdater.newUpdater(CLHLock_1::class.java, CLHNode::class.java, "head")
        private val tailUpdater = AtomicReferenceFieldUpdater.newUpdater(CLHLock_1::class.java, CLHNode::class.java, "tail")
        val logger = LoggerFactory.getLogger(CLHLock_1::class.java)
    }
}

fun main(args: Array<String>) {
    val measureNanoTime = measureNanoTime {
        val task = 1
        val threads = arrayOfNulls<Thread>(task)
        val countDownLatch = CountDownLatch(1)
        val lock = CLHLock_1()
        (task - 1).downTo(0).reversed().forEach {
            Thread {
                countDownLatch.await()
//                Thread.sleep(Random().nextInt(30).toLong())
                lock.lock()
                try {
                } finally {
                    lock.unlock()
                }
            }.apply {
                start()
                threads[it] = this
            }
        }
        countDownLatch.countDown()
        threads.forEach { it?.join() }
    }
    println("used ${measureNanoTime / 1000 / 1000} ms")
}

fun addAThread(lock: CLHLock_1): Unit {
    Thread {
        lock.lock()
        try {
        } finally {
            lock.unlock()
        }
    }.apply {
        name="added"
        start()
    }
}