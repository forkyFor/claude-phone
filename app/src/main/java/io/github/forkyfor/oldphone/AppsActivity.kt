package io.github.forkyfor.oldphone

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * The way out to the rest of the phone when this app is the Home screen: phone settings on
 * top, then every installed app. Deliberately plain: a list, no widgets, no wallpaper.
 */
class AppsActivity : BaseActivity() {
    private class Entry(val label: String, val icon: Drawable?, val launch: Intent)

    private val entries = ArrayList<Entry>()
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.column(this, 16)
        col.setBackgroundColor(Ui.color(this, R.color.bg))
        col.addView(Ui.title(this, getString(R.string.apps_title)))
        col.addView(Ui.primaryButton(this, getString(R.string.apps_settings)) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.owner_not_available, Toast.LENGTH_LONG).show()
            }
        })
        col.addView(Ui.secondaryButton(this, getString(R.string.owner_back)) {
            finish()
            noTransition()
        })
        list = ListView(this).apply {
            divider = null
            adapter = Adapter()
            setOnItemClickListener { _, _, position, _ ->
                try {
                    startActivity(entries[position].launch)
                } catch (e: Exception) {
                    Toast.makeText(this@AppsActivity, R.string.owner_not_available, Toast.LENGTH_LONG).show()
                }
            }
        }
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = Ui.dp(this@AppsActivity, 12)
        })
        setContentView(col)
        load()
    }

    /** Reads the app list off the main thread: icons are slow to load on old phones. */
    private fun load() {
        val pm = packageManager
        Thread {
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found: List<ResolveInfo> = try { pm.queryIntentActivities(query, 0) } catch (e: Exception) { emptyList() }
            val loaded = found.filter { it.activityInfo.packageName != packageName }.map {
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(it.activityInfo.packageName, it.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                Entry(it.loadLabel(pm).toString(), try { it.loadIcon(pm) } catch (e: Throwable) { null }, launch)
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                entries.clear()
                entries.addAll(loaded)
                (list.adapter as Adapter).notifyDataSetChanged()
            }
        }.start()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val c = this@AppsActivity
            val row = (convertView as? TextView) ?: TextView(c).apply {
                textSize = 18f
                setTextColor(Ui.color(c, R.color.on_bg))
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(c, 60)
                compoundDrawablePadding = Ui.dp(c, 16)
                setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6))
            }
            val e = entries[position]
            row.text = e.label
            val size = Ui.dp(c, 44)
            e.icon?.setBounds(0, 0, size, size)
            row.setCompoundDrawablesRelative(e.icon, null, null, null)
            return row
        }
    }
}
