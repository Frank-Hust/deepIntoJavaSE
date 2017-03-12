package ConcurrentLock

import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

/**
 * Created by haoyifen on 2017/3/12  15:28.
 */

open class LockSupportTest {

    @Test
    open fun test1() {
        val myThread = thread(start = true, block ={
            Thread.sleep(1000)
            println("${Thread.currentThread()} go to park")
            LockSupport.park(this)
            println("${Thread.currentThread()} unparked")
        })
//        myThread.start()
        println("unpark $myThread")
        LockSupport.unpark(myThread)
        myThread.join()
    }

    companion object {
        val Logger= LoggerFactory.getLogger(LockSupportTest::class.java)
    }
}