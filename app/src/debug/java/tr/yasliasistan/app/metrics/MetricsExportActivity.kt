package tr.yasliasistan.app.metrics

import android.app.Activity
import android.os.Bundle
import tr.yasliasistan.core.log.SafeLog
import java.io.File

/**
 * DEBUG BUILDS ONLY (src/debug): writes the metric log as CSV into the app's private files folder,
 * so the developer can copy it with adb (docs: G-18). Nothing is shared or sent; release builds do
 * not contain this class.
 *
 *   adb shell am start -n tr.yasliasistan.app/.metrics.MetricsExportActivity
 *   adb exec-out run-as tr.yasliasistan.app cat files/metrics.csv > metrics.csv
 */
class MetricsExportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = File(filesDir, FILE_NAME)
        AppMetrics.store.exportCsv { csv, rows ->
            target.writeText(csv)
            SafeLog.event(TAG, "exported $rows rows to files/$FILE_NAME")
        }
        finish()
    }

    private companion object {
        const val FILE_NAME = "metrics.csv"
        const val TAG = "Metrics"
    }
}
