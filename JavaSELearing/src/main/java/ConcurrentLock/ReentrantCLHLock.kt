package ConcurrentLock

import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.getOrSet

/**
 * Created by haoyifen on 2017/3/10  17:26.
 *
 */

class ReentrantCLHLock : Lock {
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
    var owner: AtomicReference<Thread?> = AtomicReference()
    val state: AtomicReference<Int> = AtomicReference(0)
    val nodeThreadLocal = ThreadLocal<CLHNode>()

    override fun lock() {
        val reentrant = (owner.get() == Thread.currentThread()) && state.get() > 0
        if (reentrant) {
            state.set(state.get() + 1)
            logger.info("get lock with state $state")
            return
        }
        if (state.get() == 0) {
            val result = owner.compareAndSet(null, Thread.currentThread())
            if (result) {
                state.set(1)
                logger.info("get lock with state $state")
            }
        }
        enq()
        val currentNode = nodeThreadLocal.get()
        while (true) {
            val prev = currentNode.prev
            if (prev == null) {
                currentNode.status = CLHNode.lockOwner
                logger.info("success get lock")
                return
            } else if (prev == head && prev.status == CLHNode.initial && this.owner.get() == null) {
                head = currentNode
                prev.next = null
                currentNode.status = CLHNode.lockOwner
                logger.info("success get lock")
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
        if (owner.get() != Thread.currentThread()) {
            throw IllegalStateException("${Thread.currentThread()} try to release lock $this, but it is hold by ${owner.get()}")
        }
        //不会有两个线程同时释放锁, 所以可以直接set, 而不用CAS
        val stateNow = state.get()
        state.set(stateNow - 1)
        if (stateNow > 1) {
            val currentThreadCLHNode = nodeThreadLocal.get()
            val thread = currentThreadCLHNode?.next?.thread
            logger.info("success unlock ,lock state {}, currentNode {}, next node {}", arrayOf(this.state, currentThreadCLHNode, thread))
            this.owner.set(null)
            return
        }
        val currentThreadCLHNode = nodeThreadLocal.get()
        currentThreadCLHNode.status = CLHNode.initial
        val thread = currentThreadCLHNode?.next?.thread
        this.owner.set(null)
        LockSupport.unpark(thread)
        logger.info("success unlock ,lock state {}, currentNode {}, next node {}", arrayOf(this.state, currentThreadCLHNode, thread))
    }

    companion object {

        private val headUpdater = AtomicReferenceFieldUpdater.newUpdater(ReentrantCLHLock::class.java, CLHNode::class.java, "head")
        private val tailUpdater = AtomicReferenceFieldUpdater.newUpdater(ReentrantCLHLock::class.java, CLHNode::class.java, "tail")
        val logger = LoggerFactory.getLogger(ReentrantCLHLock::class.java)
    }
}

//fun main(args: Array<String>) {
//    val reentrantCLHLock = ReentrantCLHLock()
//    val measureNanoTime = measureTimeMillis {
//        val entrantCount = 10
//        entrantCount.downTo(1).forEach {
//            reentrantCLHLock.lock()
//        }
//        entrantCount.downTo(1).forEach {
//            reentrantCLHLock.unlock()
//        }
//    }
//    println("used $measureNanoTime ms")
//}

class TestLock {

    @Test
    fun testLock() {
        val measureNanoTime = kotlin.system.measureNanoTime {
            val task = 10
            val threads = arrayOfNulls<Thread>(task)
            val countDownLatch = CountDownLatch(1)
            val lock = ReentrantCLHLock()
            (task - 1).downTo(0).reversed().forEach {
                Thread {
                    countDownLatch.await()
//                Thread.sleep(Random().nextInt(30).toLong())
                    lock.lock()
                    try {
                        try {
                            lock.lock()
                        } finally {
                            lock.unlock()
                        }
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
}