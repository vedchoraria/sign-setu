package app.signsetu

/** personal phones have no device management, so an old build stops starting sessions by itself. */
const val BUILD_LIFETIME_DAYS = 180

fun buildExpired(todayEpochDay: Long, builtEpochDay: Long) = todayEpochDay - builtEpochDay > BUILD_LIFETIME_DAYS

/** once even the newest session is past the retention period, every session is. */
fun allSessionsExpired(nowMs: Long, newestSessionAt: Long, retentionHours: Int) =
    retentionHours > 0 && newestSessionAt > 0 && nowMs - newestSessionAt > retentionHours * 3_600_000L
