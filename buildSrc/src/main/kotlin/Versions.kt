import org.gradle.api.JavaVersion
import java.util.Calendar
import java.util.TimeZone

object Versions {
    const val MinSdk = 24
    const val TargetSdk = 36
    const val CompileSdk = 36
    val JavaVersionCompat: JavaVersion = JavaVersion.VERSION_17
    const val JvmTargetVersion = "17"
    const val DebugVersionCode: Int = 2090000000
    val VersionCode: Int
        get() {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            // Generate version code that stays within Google Play's limit
            // Format: (year-2020) * 100000000 + month * 1000000 + day * 10000 + hour * 100 + minute
            // This gives us a unique, incrementing version code based on build time
            val baseYear = 2020
            val yearOffset = maxOf(0, year - baseYear) // Ensure we don't go negative

            // Calculate version code components
            val versionCode = yearOffset * 100000000 +
                    month * 1000000 +
                    day * 10000 +
                    hour * 100 +
                    minute

            // Ensure we don't exceed Google Play's maximum version code (2100000000)
            return minOf(versionCode, 2099999999)
        }
}