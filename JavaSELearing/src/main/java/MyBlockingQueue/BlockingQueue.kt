package MyBlockingQueue

/**
 * Created by haoyifen on 2017/3/12  20:06.
 */
interface BlockingQueue<T> {
    val size: Int
    fun put(e: T)
    fun take(): T
}