package com.v2ray.ang.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import java.util.ArrayList
import com.v2ray.ang.R
import com.v2ray.ang.util.AngConfigManager
import android.content.Intent
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.google.zxing.WriterException
import com.v2ray.ang.AppConfig
import kotlinx.android.synthetic.main.activity_tasker.*


class TaskerActivity : BaseActivity() {
    private var listview: ListView? = null
    private var lstData: ArrayList<String> = ArrayList()
    private var lstGuid: ArrayList<String> = ArrayList()
    private val vmess = AngConfigManager.configs.vmess

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker)

        vmess.forEach {
            lstData.add(it.remarks)
            lstGuid.add(it.guid)
        }
        val adapter = ArrayAdapter(this,
                android.R.layout.simple_list_item_single_choice, lstData)
        listview = findViewById<View>(R.id.listview) as ListView
        listview!!.adapter = adapter

        init()
    }

    private fun init() {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                switch_start_service.isChecked = switch
                val pos = lstGuid.indexOf(guid.toString())
                if (pos >= 0) {
                    listview?.setItemChecked(pos, true)
                }
            }
        } catch (e: WriterException) {
            e.printStackTrace()

        }
    }

    private fun confirmFinish() {
        val position = listview?.checkedItemPosition
        if (position == null || position < 0) {
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, switch_start_service.isChecked)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, vmess[position].guid)
        val intent = Intent()

        val remarks = vmess[position].remarks
        var blurb = ""

        if (switch_start_service.isChecked) {
            blurb = "Start $remarks"
        } else {
            blurb = "Stop $remarks"
        }

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val del_config = menu?.findItem(R.id.del_config)
        del_config?.isVisible = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            true
        }
        R.id.save_config -> {
            confirmFinish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

}

