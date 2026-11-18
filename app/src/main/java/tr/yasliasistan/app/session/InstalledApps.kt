package tr.yasliasistan.app.session

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.decision.InstalledAppsSource
import tr.yasliasistan.core.log.SafeLog

/**
 * Launcher entries of the phone, found with the MAIN/LAUNCHER intent declared in the manifest's
 * `<queries>` (no QUERY_ALL_PACKAGES, NFG-68). The list stays on the device and is never logged (NFG-71).
 */
class PackageManagerAppsSource(context: Context) : InstalledAppsSource {
    private val pm: PackageManager = context.packageManager

    override fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return found.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val label = info.loadLabel(pm).toString().trim()
            if (label.isEmpty()) null else InstalledApp(label, activity.packageName, activity.name)
        }
    }
}

/**
 * Keeps the app list for [ttlMs], so a session's prefetch makes the decision instant
 * while installs and removals are still seen soon after. Thread-safe.
 */
class CachedAppsSource(
    private val source: InstalledAppsSource,
    private val now: () -> Long,
    private val ttlMs: Long = TTL_MS,
) : InstalledAppsSource {
    private var cached: List<InstalledApp>? = null
    private var loadedAt = 0L

    @Synchronized
    override fun launchableApps(): List<InstalledApp> {
        val current = cached
        if (current != null && now() - loadedAt < ttlMs) return current
        return source.launchableApps().also {
            cached = it
            loadedAt = now()
        }
    }

    companion object {
        const val TTL_MS: Long = 60_000
    }
}

/** Opens an app with its launcher intent (Low risk, undone with Back/Home; FG-71). */
fun Context.launchApp(app: InstalledApp): Boolean {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        SafeLog.event("Launch", "launch failed: not found", SafeLog.Level.WARN)
        false
    } catch (e: SecurityException) {
        SafeLog.event("Launch", "launch failed: not allowed", SafeLog.Level.WARN)
        false
    }
}
