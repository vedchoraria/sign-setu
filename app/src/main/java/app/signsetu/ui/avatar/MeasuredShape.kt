package app.signsetu.ui.avatar

import android.util.LruCache

private val shapeCache = LruCache<String, HandShape>(128)

/**
 * A named hand shape, or one measured from a signer (isl_signs.json): "c:" then thumb, index, middle, ring and
 * pinky curl, thumbAcross and spread, in percent. Anything else is unknown and plays as the relaxed hand.
 * Results are cached in an LRU cache to eliminate string splitting overhead on every animation frame.
 */
fun handShape(name: String): HandShape? {
    HandShape.ALL[name]?.let { return it }
    shapeCache.get(name)?.let { return it }
    if (!name.startsWith("c:")) return null
    val v = name.substring(2).split(',').map { it.trim().toIntOrNull() ?: return null }
    if (v.size != 7) return null
    fun curl(i: Int) = (v[i] / 100f).coerceIn(0f, 1f)
    val parsed = HandShape(curl(0), curl(1), curl(2), curl(3), curl(4), curl(5), (v[6] / 100f).coerceIn(-1f, 1f))
    shapeCache.put(name, parsed)
    return parsed
}
