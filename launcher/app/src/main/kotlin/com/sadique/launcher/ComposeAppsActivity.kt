package com.sadique.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.text.Collator
import java.util.concurrent.Executors

/** Optional Compose screen. The TV Home alias still opens the established launcher. */
class ComposeAppsActivity : ComponentActivity() {
    private data class App(val label: String, val component: String, val intent: Intent)
    private val loader = Executors.newSingleThreadExecutor()
    private var apps by mutableStateOf<List<App>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.compose_apps_title), style = MaterialTheme.typography.headlineLarge)
                        val visibleApps = apps
                        if (visibleApps == null) {
                            Text(stringResource(R.string.compose_apps_loading))
                        } else if (visibleApps.isEmpty()) {
                            Text(stringResource(R.string.compose_apps_empty))
                        } else {
                            val firstTile = remember { FocusRequester() }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(165.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(visibleApps, key = { _, app -> app.component }) { index, app ->
                                    Button(
                                        onClick = { open(app) },
                                        modifier = Modifier.fillMaxWidth().then(
                                            if (index == 0) Modifier.focusRequester(firstTile) else Modifier
                                        )
                                    ) {
                                        Text(app.label, maxLines = 2)
                                    }
                                }
                            }
                            LaunchedEffect(firstTile) { firstTile.requestFocus() }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loader.execute {
            val hidden = ThemePreferences.get(this).getStringSet("hidden_apps", emptySet()).orEmpty()
            val activities = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { category ->
                    packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                        .map { category to it }
                }
                .filter { (_, result) ->
                    result.activityInfo.packageName != packageName && result.activityInfo.packageName !in hidden
                }
                .distinctBy { (_, result) -> result.activityInfo.packageName }
                .map { (category, result) ->
                    val info = result.activityInfo
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setClassName(info.packageName, info.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    App(result.loadLabel(packageManager).toString(), "${info.packageName}/${info.name}", intent)
                }
            val collator = Collator.getInstance()
            runOnUiThread {
                if (!isDestroyed) apps = activities.sortedWith(compareBy(collator) { it.label })
            }
        }
    }

    private fun open(app: App) {
        try {
            startActivity(app.intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        loader.shutdownNow()
        super.onDestroy()
    }
}
