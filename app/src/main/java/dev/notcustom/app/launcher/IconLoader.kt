package dev.notcustom.app.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.util.LruCache
import android.util.Xml
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class IconPackInfo(val packageName: String, val label: String)

class IconLoader(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(android.content.pm.LauncherApps::class.java)
    private val cache = LruCache<String, ImageBitmap>(160)
    private val semaphore = Semaphore(6)
    private val appFilters = ConcurrentHashMap<String, Map<String, String>>()

    @Volatile
    private var selectedPack: String? = null

    fun setSelectedPack(packageName: String?) {
        selectedPack = packageName
    }

    fun peek(key: String): ImageBitmap? = cache.get(cacheKey(key, selectedPack))

    fun clear() {
        cache.evictAll()
        appFilters.clear()
    }

    @Suppress("DEPRECATION")
    fun availablePacks(): List<IconPackInfo> {
        val intents = listOf(
            Intent("org.adw.ActivityStarter.THEMES"),
            Intent("org.adw.launcher.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("com.anddoes.launcher.THEME"),
            Intent("com.teslacoilsw.launcher.THEME"),
            Intent("com.actionlauncher.THEME"),
            Intent("com.dlto.atom.launcher.THEME"),
            Intent("com.gau.go.launcherex.theme"),
            Intent("ch.deletescape.lawnchair.ICONPACK"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("home.solo.launcher.free.THEMES"),
            Intent(Intent.ACTION_MAIN).addCategory("com.gridappsinc.launcher.theme.apk_action")
        )
        val pm = appContext.packageManager
        val packages = intents.flatMap { intent ->
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        }.distinct()
        return packages.mapNotNull { packageName ->
            runCatching {
                val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                IconPackInfo(packageName, label)
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    suspend fun load(app: LauncherApp): ImageBitmap? = withContext(Dispatchers.IO) {
        val pack = selectedPack
        val key = cacheKey(app.key, pack)
        cache.get(key)?.let { return@withContext it }
        semaphore.withPermit {
            cache.get(key)?.let { return@withPermit it }
            val density = appContext.resources.displayMetrics.densityDpi
            val pm = appContext.packageManager
            val activityInfo = runCatching {
                launcherApps.getActivityList(app.packageName, app.user)
                    .firstOrNull { it.componentName == app.component }
            }.getOrNull()
            val drawable = pack?.let { iconFromPack(it, app.component) }
                ?: activityInfo?.getIcon(density)
                ?: runCatching { pm.getActivityIcon(app.component) }.getOrNull()
                ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
                ?: return@withPermit null
            val badged = runCatching { pm.getUserBadgedIcon(drawable, app.user) }
                .getOrNull() ?: drawable
            val bitmap = runCatching {
                badged.toBitmap(width = 168, height = 168).asImageBitmap()
            }.getOrNull()
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }
    }

    private fun iconFromPack(packageName: String, component: ComponentName): android.graphics.drawable.Drawable? {
        val appFilter = appFilters.getOrPut(packageName) { loadAppFilter(packageName) }
        val componentKey = component.flattenToString()
        val drawableName = appFilter[componentKey]
            ?: appFilter["ComponentInfo{$componentKey}"]
            ?: return null
        val packContext = runCatching {
            appContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val resources = packContext.resources
        val resourceId = sequenceOf("drawable", "mipmap")
            .map { type -> resources.getIdentifier(drawableName, type, packageName) }
            .firstOrNull { it != 0 }
            ?: return null
        return runCatching { resources.getDrawable(resourceId, packContext.theme) }.getOrNull()
    }

    private fun loadAppFilter(packageName: String): Map<String, String> {
        val packContext = runCatching {
            appContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return emptyMap()
        val resources = packContext.resources
        val xmlResource = resources.getIdentifier("appfilter", "xml", packageName)
        if (xmlResource != 0) {
            val parsed = runCatching { resources.getXml(xmlResource).useParser(::parseAppFilter) }
                .getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        val rawResource = resources.getIdentifier("appfilter", "raw", packageName)
        if (rawResource != 0) {
            val parsed = runCatching {
                resources.openRawResource(rawResource).use { stream ->
                    parseAppFilter(Xml.newPullParser().apply { setInput(stream, "UTF-8") })
                }
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        val parsed = runCatching {
            packContext.assets.open("appfilter.xml").use { stream ->
                parseAppFilter(Xml.newPullParser().apply { setInput(stream, "UTF-8") })
            }
        }.getOrNull()
        return parsed.orEmpty()
    }

    private fun XmlResourceParser.useParser(parse: (XmlPullParser) -> Map<String, String>) =
        try {
            parse(this)
        } finally {
            close()
        }

    private fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
        val result = HashMap<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val rawComponent = parser.getAttributeValue(null, "component") ?: continue
            val drawable = parser.getAttributeValue(null, "drawable") ?: continue
            val component = rawComponent
                .removePrefix("ComponentInfo{")
                .removeSuffix("}")
            val key = ComponentName.unflattenFromString(component)?.flattenToString() ?: component
            result[key] = drawable
        }
        return result
    }

    private fun cacheKey(appKey: String, pack: String?): String = "${pack.orEmpty()}::$appKey"
}
