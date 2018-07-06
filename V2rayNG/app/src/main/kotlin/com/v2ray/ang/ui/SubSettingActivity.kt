package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.v2ray.ang.R
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_sub_setting.*
import org.jetbrains.anko.*


class SubSettingActivity : BaseActivity() {

    var del_config: MenuItem? = null
    var save_config: MenuItem? = null

    private lateinit var configs: AngConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_setting)

        configs = AngConfigManager.configs
        title = getString(R.string.title_sub_setting)

        bindingServer()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * bingding seleced server config
     */
    fun bindingServer(): Boolean {
        for (k in configs.subItem.count()..2) {
            val subItem = AngConfig.SubItemBean()
            configs.subItem.add(subItem)
        }

        et_remarks.text = Utils.getEditable(configs.subItem[0].remarks)
        et_url.text = Utils.getEditable(configs.subItem[0].url)

        et_remarks2.text = Utils.getEditable(configs.subItem[1].remarks)
        et_url2.text = Utils.getEditable(configs.subItem[1].url)

        et_remarks3.text = Utils.getEditable(configs.subItem[2].remarks)
        et_url3.text = Utils.getEditable(configs.subItem[2].url)

        return true
    }

    /**
     * save server config
     */
    fun saveServer(): Boolean {

        configs.subItem[0].remarks = et_remarks.text.toString().trim()
        configs.subItem[0].url = et_url.text.toString().trim()

        configs.subItem[1].remarks = et_remarks2.text.toString().trim()
        configs.subItem[1].url = et_url2.text.toString().trim()

        configs.subItem[2].remarks = et_remarks3.text.toString().trim()
        configs.subItem[2].url = et_url3.text.toString().trim()


        if (AngConfigManager.saveSubItem(configs.subItem) == 0) {
            toast(R.string.toast_success)
            finish()
            return true
        } else {
            toast(R.string.toast_failure)
            return false
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        del_config = menu?.findItem(R.id.del_config)
        save_config = menu?.findItem(R.id.save_config)

        del_config?.isVisible = false

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}