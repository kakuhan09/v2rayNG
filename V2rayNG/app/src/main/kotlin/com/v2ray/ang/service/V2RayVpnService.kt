package com.v2ray.ang.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.*
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.ui.SettingsActivity
import com.v2ray.ang.util.AssetsUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import libv2ray.Libv2ray
import libv2ray.V2RayCallbacks
import libv2ray.V2RayVPNServiceSupportsSet
import java.lang.ref.SoftReference

class V2RayVpnService : VpnService() {
    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
        const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1

        fun startV2Ray(context: Context) {
            val intent = Intent(context.applicationContext, V2RayVpnService::class.java)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val v2rayPoint = Libv2ray.newV2RayPoint()
    private val v2rayCallback = V2RayCallback()
    //    private var connectivitySubscription: Subscription? = null
//    private var netWorkStateReceiver: NetWorkStateReceiver? = null
    private lateinit var configContent: String
    private lateinit var mInterface: ParcelFileDescriptor
    val fd: Int get() = mInterface.fd
    private var currentTimeMillis: Long = 0

    override fun onCreate() {
        super.onCreate()

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        v2rayPoint.packageName = packageName
    }

    override fun onRevoke() {
        stopV2Ray()
    }

    fun setup(parameters: String) {
        // If the old interface has exactly the same parameters, use it!
        // Configure a builder while parsing the parameters.
        val builder = Builder()

        parameters.split(" ")
                .map { it.split(",") }
                .forEach {
                    when (it[0][0]) {
                        'm' -> builder.setMtu(java.lang.Short.parseShort(it[1]).toInt())
                        'a' -> builder.addAddress(it[1], Integer.parseInt(it[2]))
                        'r' -> builder.addRoute(it[1], Integer.parseInt(it[2]))
                        's' -> builder.addSearchDomain(it[1])
                    }
                }

        builder.setSession(defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_NAME, ""))

        val dnsServers = Utils.getRemoteDnsServers(defaultDPreference)
        for (dns in dnsServers) {
            builder.addDnsServer(dns)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                defaultDPreference.getPrefBoolean(SettingsActivity.PREF_PER_APP_PROXY, false)) {
            val apps = defaultDPreference.getPrefStringSet(PerAppProxyActivity.PREF_PER_APP_PROXY_SET, null)
            val bypassApps = defaultDPreference.getPrefBoolean(PerAppProxyActivity.PREF_BYPASS_APPS, false)
            apps?.forEach {
                try {
                    if (bypassApps)
                        builder.addDisallowedApplication(it)
                    else
                        builder.addAllowedApplication(it)
                } catch (e: PackageManager.NameNotFoundException) {
                    //Logger.d(e)
                }
            }
        }

        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close()
        } catch (ignored: Exception) {
        }

        // Create a new interface using the builder and save the parameters.
        mInterface = builder.establish()
        //Logger.d("VPNService", "New interface: " + parameters)
        //Logger.d(Libv2ray.checkVersionX())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        restartV2Ray()

        return START_STICKY
        //return super.onStartCommand(intent, flags, startId)
    }

    private fun vpnCheckIsReady() {
        val prepare = VpnService.prepare(this)

        if (prepare != null) {
            return
        }

        v2rayPoint.vpnSupportReady()
        if (v2rayPoint.isRunning) {
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_START_SUCCESS, "")
//            showNotification()
        } else {
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_START_FAILURE, "")
            cancelNotification()
        }
    }

    private fun startV2ray(isForced: Boolean = false) {
        if (!v2rayPoint.isRunning || isForced) {

            try {
                registerReceiver(mMsgReceive, IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE))
            } catch (e: Exception) {
            }

            configContent = defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")

//            connectivitySubscription = ReactiveNetwork.observeNetworkConnectivity(this.applicationContext)
//                    .subscribeOn(Schedulers.io())
//                    //.filter(Connectivity.hasState(NetworkInfo.State.CONNECTED))
//                    //.throttleWithTimeout(3, TimeUnit.SECONDS)
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe { connectivity ->
//                        val state = connectivity.state
//                        Logger.e(state.toString())
//                        //if (state == NetworkInfo.State.CONNECTED) {
//                        if (v2rayPoint.isRunning) {
//                            v2rayPoint.networkInterrupted()
//                        }
//                        //}
//
            v2rayPoint.callbacks = v2rayCallback
//            v2rayPoint.vpnSupportSet = v2rayCallback
            v2rayPoint.setVpnSupportSet(v2rayCallback)

            v2rayPoint.configureFile = "V2Ray_internal/ConfigureFileContent"
            v2rayPoint.configureFileContent = configContent

            //next gen tun2ray
//            v2rayPoint.upgradeToContext()
//            v2rayPoint.optinNextGenerationTunInterface()

            v2rayPoint.runLoop()
        }

        showNotification()
    }

    private fun stopV2Ray(isForced: Boolean = true) {
//        val configName = defaultDPreference.getPrefString(PREF_CURR_CONFIG_GUID, "")
//        val emptyInfo = VpnNetworkInfo()
//        val info = loadVpnNetworkInfo(configName, emptyInfo)!! + (lastNetworkInfo ?: emptyInfo)
//        saveVpnNetworkInfo(configName, info)

        if (v2rayPoint.isRunning) {
            v2rayPoint.stopLoop()
        }

//        unregisterReceiver(netWorkStateReceiver)

//        connectivitySubscription?.let {
//            it.unsubscribe()
//            connectivitySubscription = null
//        }
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        cancelNotification()

        if (isForced) {
            try {
                unregisterReceiver(mMsgReceive)
            } catch (e: Exception) {
            }
            stopSelf()
        }
    }

    private fun restartV2Ray() {
        try {
            //use custom geo dat
//            val path = AssetsUtil.getAssetPath(this, "geoip.dat")
//            Libv2ray.setAssetsOverride("geoip.dat", path)
//
//            val path2 = AssetsUtil.getAssetPath(this, "geosite.dat")
//            Libv2ray.setAssetsOverride("geosite.dat", path2)

            stopV2Ray(false)
            startV2ray(true)
        } catch (e: Exception) {
        }
    }

    private fun restartV2RaySoft() {
        if (System.currentTimeMillis() > currentTimeMillis + 2000) {
            if (v2rayPoint.isRunning) {
                v2rayPoint.networkInterrupted()
            }
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    private fun showNotification() {
        val startMainIntent = Intent(applicationContext, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(applicationContext,
                NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)

        val stopV2RayPendingIntent = PendingIntent.getBroadcast(applicationContext,
                NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_v)
                .setContentTitle(defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_NAME, ""))
                .setContentText(getString(R.string.notification_action_more))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_close_grey_800_24dp,
                        getString(R.string.notification_action_stop_v2ray),
                        stopV2RayPendingIntent)
                .build()

        startForeground(NOTIFICATION_ID, notification)

//        if (netWorkStateReceiver == null) {
//            netWorkStateReceiver = NetWorkStateReceiver()
//        }
//        registerReceiver(netWorkStateReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "RAY_NG_M_CH_ID"
        val channelName = "V2rayNG Background Service"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun cancelNotification() {
        stopForeground(true)
    }

    private inner class V2RayCallback : V2RayCallbacks, V2RayVPNServiceSupportsSet {
        override fun shutdown() = 0L

        override fun getVPNFd() = this@V2RayVpnService.fd.toLong()

        override fun prepare(): Long {
            vpnCheckIsReady()
            return 1
        }

        override fun protect(l: Long) = (if (this@V2RayVpnService.protect(l.toInt())) 0 else 1).toLong()

        override fun onEmitStatus(l: Long, s: String?): Long {
            //Logger.d(s)
            return 0
        }

        override fun setup(s: String): Long {
            //Logger.d(s)
            try {
                this@V2RayVpnService.setup(s)
                return 0
            } catch (e: Exception) {
                e.printStackTrace()
                return -1
            }
        }
    }

    private var mMsgReceive = ReceiveMessageHandler(this@V2RayVpnService)

    private class ReceiveMessageHandler(vpnService: V2RayVpnService) : BroadcastReceiver() {
        internal var mReference: SoftReference<V2RayVpnService> = SoftReference(vpnService)

        override fun onReceive(ctx: Context?, intent: Intent?) {
            val vpnService = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    //Logger.e("ReceiveMessageHandler", intent?.getIntExtra("key", 0).toString())

                    val isRunning = vpnService?.v2rayPoint!!.isRunning
                            && VpnService.prepare(vpnService) == null
                    if (isRunning) {
                        MessageUtil.sendMsg2UI(vpnService, AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(vpnService, AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }
                AppConfig.MSG_UNREGISTER_CLIENT -> {
//                    vpnService?.mMsgSend = null
                }
                AppConfig.MSG_STATE_START -> {
                    //nothing to do
                }
                AppConfig.MSG_STATE_STOP -> {
                    vpnService?.stopV2Ray()
                }
                AppConfig.MSG_STATE_RESTART -> {
                    vpnService?.restartV2Ray()
                }
                AppConfig.MSG_STATE_RESTART_SOFT -> {
                    vpnService?.restartV2RaySoft()
                }
            }
        }
    }
}

