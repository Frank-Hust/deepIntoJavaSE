package ConcurrentLock

/**
 * Created by haoyifen on 2017/3/10  15:35.
 */
interface Lock {
    fun lock()
    fun unlock()
}