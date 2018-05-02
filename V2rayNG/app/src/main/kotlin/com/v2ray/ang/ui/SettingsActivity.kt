package com.v2ray.ang.ui

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.InappBuyActivity
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.onClick
import com.v2ray.ang.util.Utils
import libv2ray.Libv2ray
import org.jetbrains.anko.act
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.startActivity

class SettingsActivity : BaseActivity() {
    companion object {
        //        const val PREF_BYPASS_MAINLAND = "pref_bypass_mainland"
        //        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
        const val PREF_MUX_ENABLED = "pref_mux_enabled"
        const val PREF_REMOTE_DNS = "pref_remote_dns"
//        const val PREF_SPEEDUP_DOMAIN = "pref_speedup_domain"

        const val PREF_ROUTING_MODE = "pref_routing_mode"
        const val PREF_ROUTING = "pref_routing"
        const val PREF_DONATE = "pref_donate"
        //        const val PREF_LICENSES = "pref_licenses"
        const val PREF_FEEDBACK = "pref_feedback"
        const val PREF_VERSION = "pref_version"
        //        const val PREF_AUTO_RESTART = "pref_auto_restart"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
        val perAppProxy by lazy { findPreference(PREF_PER_APP_PROXY) as CheckBoxPreference }
        //        val autoRestart by lazy { findPreference(PREF_AUTO_RESTART) as CheckBoxPreference }
        val remoteDns by lazy { findPreference(PREF_REMOTE_DNS) as EditTextPreference }

        val routing: Preference by lazy { findPreference(PREF_ROUTING) }
        val donate: Preference by lazy { findPreference(PREF_DONATE) }
        //        val licenses: Preference by lazy { findPreference(PREF_LICENSES) }
        val feedback: Preference by lazy { findPreference(PREF_FEEDBACK) }
        val version: Preference by lazy { findPreference(PREF_VERSION) }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_settings)

            routing.onClick {
                startActivity<RoutingSettingsActivity>()
            }

            donate.onClick {
                startActivity<InappBuyActivity>()
            }

//            licenses.onClick {
//                val fragment = LicensesDialogFragment.Builder(act)
//                        .setNotices(R.raw.licenses)
//                        .setIncludeOwnLicense(false)
//                        .build()
//                fragment.show((act as AppCompatActivity).supportFragmentManager, null)
//            }

            feedback.onClick {
                Utils.openUri(activity, "https://github.com/2dust/v2rayNG/issues")
            }

            perAppProxy.setOnPreferenceClickListener {
                startActivity<PerAppProxyActivity>()
                perAppProxy.isChecked = true
                false
            }

            remoteDns.setOnPreferenceChangeListener { preference, any ->
                remoteDns.summary = any as String
                true
            }

            version.summary = "${BuildConfig.VERSION_NAME} (${Libv2ray.checkVersionX()})"
        }

        override fun onStart() {
            super.onStart()

            perAppProxy.isChecked = defaultSharedPreferences.getBoolean(PREF_PER_APP_PROXY, false)
            remoteDns.summary = defaultSharedPreferences.getString(PREF_REMOTE_DNS, "")

            defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onStop() {
            super.onStop()
            defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            when (key) {
//                PREF_AUTO_RESTART ->
//                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))

                PREF_PER_APP_PROXY ->
                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))
            }
        }
    }

}