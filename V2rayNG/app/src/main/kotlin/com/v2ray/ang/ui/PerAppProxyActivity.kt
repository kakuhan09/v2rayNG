package com.v2ray.ang.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.dinuscxj.itemdecoration.LinearDividerItemDecoration
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.util.AppManagerUtil
import kotlinx.android.synthetic.main.activity_bypass_list.*
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.text.Collator
import java.util.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.v2ray.ang.dto.AppInfo

class PerAppProxyActivity : BaseActivity() {
    companion object {
        const val PREF_PER_APP_PROXY_SET = "pref_per_app_proxy_set"
        const val PREF_BYPASS_APPS = "pref_bypass_apps"
    }

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bypass_list)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val dividerItemDecoration = LinearDividerItemDecoration(
                this, LinearDividerItemDecoration.LINEAR_DIVIDER_VERTICAL)
        recycler_view.addItemDecoration(dividerItemDecoration)

        AppManagerUtil.rxLoadNetworkAppList(this)
                .subscribeOn(Schedulers.io())
                .map {
                    val comparator = object : Comparator<AppInfo> {
                        val collator = Collator.getInstance()
                        override fun compare(o1: AppInfo, o2: AppInfo)
                                = collator.compare(o1.appName, o2.appName)
                    }
                    it.sortedWith(comparator)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    appsAll = it
                    val blacklist = defaultDPreference.getPrefStringSet(PREF_PER_APP_PROXY_SET, null)
                    adapter = PerAppProxyAdapter(this, it, blacklist)
                    recycler_view.adapter = adapter
                    pb_waiting.visibility = View.GONE
                }

        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            var dst = 0
            val threshold = resources.getDimensionPixelSize(R.dimen.bypass_list_header_height) * 3
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                dst += dy
                if (dst > threshold) {
                    header_view.hide()
                    dst = 0
                } else if (dst < -20) {
                    header_view.show()
                    dst = 0
                }
            }

            var hiding = false
            fun View.hide() {
                val target = -height.toFloat()
                if (hiding || translationY == target) return
                animate()
                        .translationY(target)
                        .setInterpolator(AccelerateInterpolator(2F))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                hiding = false
                            }
                        })
                hiding = true
            }

            var showing = false
            fun View.show() {
                val target = 0f
                if (showing || translationY == target) return
                animate()
                        .translationY(target)
                        .setInterpolator(DecelerateInterpolator(2F))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                showing = false
                            }
                        })
                showing = true
            }
        })

        switch_per_app_proxy.setOnCheckedChangeListener { buttonView, isChecked ->
            defaultDPreference.setPrefBoolean(SettingsActivity.PREF_PER_APP_PROXY, isChecked)
        }
        switch_per_app_proxy.isChecked = defaultDPreference.getPrefBoolean(SettingsActivity.PREF_PER_APP_PROXY, false)

        switch_bypass_apps.setOnCheckedChangeListener { buttonView, isChecked ->
            defaultDPreference.setPrefBoolean(PREF_BYPASS_APPS, isChecked)
        }
        switch_bypass_apps.isChecked = defaultDPreference.getPrefBoolean(PREF_BYPASS_APPS, false)

        et_search.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                //hide
                var imm: InputMethodManager = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)

                val key = v.text.toString().toUpperCase()
                val apps = ArrayList<AppInfo>()
                if (TextUtils.isEmpty(key)) {
                    appsAll?.forEach {
                        apps.add(it)
                    }
                } else {
                    appsAll?.forEach {
                        if (it.appName.toUpperCase().indexOf(key) >= 0) {
                            apps.add(it)
                        }
                    }
                }
                adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
                recycler_view.adapter = adapter
                adapter?.notifyDataSetChanged()
                true
            } else {
                false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.let {
            defaultDPreference.setPrefStringSet(PREF_PER_APP_PROXY_SET, it.blacklist)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> adapter?.let {
            val pkgNames = it.apps.map { it.packageName }
            if (it.blacklist.containsAll(pkgNames)) {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist!!.remove(packageName)
                }
            } else {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist!!.add(packageName)
                }

            }
            it.notifyDataSetChanged()
            true
        } ?: false

        else -> super.onOptionsItemSelected(item)
    }

}