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
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.VpnBandwidth
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.ui.SettingsActivity
import com.v2ray.ang.util.MessageUtil
import rx.Observable
import rx.Subscription
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.ref.SoftReference
import tun2socks.PacketFlow
import tun2socks.Tun2socks
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import tun2socks.VpnService as Tun2socksVpnService

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

    private val TAG = "V2RayVpnService:GoLog"
    private lateinit var domainName: String
    private lateinit var configContent: String
    private var mBuilder: NotificationCompat.Builder? = null
    private var mSubscription: Subscription? = null
    private var lastVpnBandwidth: VpnBandwidth? = null
    private var mNotificationManager: NotificationManager? = null
    var isRunning = false

    var proxyDomainIPMap: HashMap<String, String> = HashMap<String, String>()
    var pfd: ParcelFileDescriptor? = null
    var inputStream: FileInputStream? = null
    var outputStream: FileOutputStream? = null
    var buffer = ByteBuffer.allocate(1501)

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
    }

    override fun onRevoke() {
        stopV2Ray()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        startV2ray()
        return START_STICKY
    }

    private fun startV2ray() {
        if (isRunning) {
            Log.d(TAG, "isRunning")
            return
        }

        Log.d(TAG, "check prepare")
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            return
        }

        Log.d(TAG, "registerReceiver mMsgReceive")
        try {
            registerReceiver(mMsgReceive, IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE))
        } catch (e: Exception) {
        }

        domainName = defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_DOMAIN, "")
        configContent = defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
        Log.d(TAG, domainName)

        try {
            val addr = InetAddress.getByName(domainName)
            val ip = addr.getHostAddress()
            if (domainName != ip) {
                proxyDomainIPMap.put(domainName, ip)
                Log.d(TAG, ip)
            }
        } catch (e: Exception) {
            Log.d(TAG, e.message)
            return
        }
        Log.d(TAG, "get InetAddress ok")


        val builder = Builder()
        builder.setSession("vv")
                .setMtu(1500)
                .addAddress("10.233.233.233", 30)
                .addDnsServer("223.5.5.5")
                .addSearchDomain("local")
                .addRoute("0.0.0.0", 0)
//        builder.setBlocking(true)

        builder.setSession(defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_NAME, ""))

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
                    Log.d(TAG, e.message)
                }
            }
        }

        pfd = builder.establish()
        Log.d(TAG, "pfd:" + pfd.toString())

        // Put the tunFd in blocking mode. Since we are reading packets from this fd in the
        // main loop, failing to do this will cause very high CPU utilization, which is
        // absolutely not what we want. Doing this in Go code because Android only has
        // limited support for this feature, which requires API level >= 21.
        if ((pfd == null) || !tun2socks.Tun2socks.setNonblock(pfd!!.fd.toLong(), false)) {
            Log.d(TAG, "failed to put tunFd in blocking mode")
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_START_FAILURE, "")
            return
        }

        inputStream = FileInputStream(pfd?.fileDescriptor)
        outputStream = FileOutputStream(pfd?.fileDescriptor)

        val flow = Flow(outputStream)
        val service = Service(this)

        val files = filesDir.list()
        if (!files.contains("geoip.dat") || !files.contains("geosite.dat")) {
            Log.d(TAG, "not contains geo files")
            val geoipBytes = resources.openRawResource(R.raw.geoip).readBytes()
            val fos = openFileOutput("geoip.dat", Context.MODE_PRIVATE)
            fos.write(geoipBytes)
            fos.close()

            val geositeBytes = resources.openRawResource(R.raw.geosite).readBytes()
            val fos2 = openFileOutput("geosite.dat", Context.MODE_PRIVATE)
            fos2.write(geositeBytes)
            fos2.close()
        }
        val serverDomains = proxyDomainIPMap.keys.joinToString(separator = ",")
        val serverIPs = proxyDomainIPMap.values.joinToString(separator = ",")

        Log.d(TAG, "start Tun2socks")
        //tun2socks.Tun2socks.startV2Ray(flow, service, configContent.toByteArray(), filesDir.absolutePath)
        Tun2socks.startV2Ray(flow, service, configContent.toByteArray(), filesDir.absolutePath, serverDomains, serverIPs)
        Log.d(TAG, "success Tun2socks")
        isRunning = true

        MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_START_SUCCESS, "")
        showNotification()

        if (defaultDPreference.getPrefBoolean(SettingsActivity.PREF_SPEED_ENABLED, false)) {
            mSubscription = Observable.interval(3, java.util.concurrent.TimeUnit.SECONDS)
                    .subscribe {
                        vpnBandwidth?.let {
                            lastVpnBandwidth?.let { last ->
                                val speed = it - last
                                updateNotification("${(speed.txByte / 3).toSpeedString()} ↑  ${(speed.rxByte / 3).toSpeedString()} ↓")
                            }
                            lastVpnBandwidth = it
                        }
                    }
        }

        thread(start = true) {
            Log.d(TAG, "handlePackets")
            handlePackets()
            Log.d(TAG, "end handlePackets")
        }
    }

    private fun stopV2Ray() {
//        val configName = defaultDPreference.getPrefString(PREF_CURR_CONFIG_GUID, "")
//        val emptyInfo = VpnNetworkInfo()
//        val info = loadVpnNetworkInfo(configName, emptyInfo)!! + (lastNetworkInfo ?: emptyInfo)
//        saveVpnNetworkInfo(configName, info)

        if (isRunning) {
            isRunning = false
            Tun2socks.stopV2Ray()
            pfd?.close()
            pfd = null
            inputStream = null
            outputStream = null

            MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_STOP_SUCCESS, "")
            cancelNotification()

            try {
                unregisterReceiver(mMsgReceive)
            } catch (e: Exception) {
            }
            stopSelf()
        }
    }

    class Flow(stream: FileOutputStream?) : PacketFlow {
        val flowOutputStream = stream
        override fun writePacket(pkt: ByteArray?) {
            flowOutputStream?.write(pkt)
        }
    }

    class Service(service: VpnService) : Tun2socksVpnService {
        val vpnService = service
        override fun protect(fd: Long): Boolean {
            return vpnService.protect(fd.toInt())
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

        mBuilder = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_v)
                .setContentTitle(defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_NAME, ""))
                .setContentText(getString(R.string.notification_action_more))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_close_grey_800_24dp,
                        getString(R.string.notification_action_stop_v2ray),
                        stopV2RayPendingIntent)
        //.build()

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)  //取消震动,铃声其他都不好使

        startForeground(NOTIFICATION_ID, mBuilder?.build())
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
        getNotificationManager().createNotificationChannel(chan)
        return channelId
    }

    private fun cancelNotification() {
        stopForeground(true)
        mBuilder = null
        mSubscription?.unsubscribe()
        mSubscription = null
    }

    private fun updateNotification(contentText: String) {
        if (mBuilder != null) {
            mBuilder?.setContentText(contentText)
            getNotificationManager().notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    private fun getNotificationManager(): NotificationManager {
        if (mNotificationManager == null) {
            mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager!!
    }

    private val vpnBandwidth: VpnBandwidth?
        get() {
            try {
                val netDev = FileInputStream("/proc/net/dev").bufferedReader()
                var bandWidth: VpnBandwidth? = null
                val prefix = "tun0:"
                while (true) {
                    val line = netDev.readLine().trim()
                    if (line.startsWith(prefix)) {
                        val numbers = line.substring(prefix.length).split(' ')
                                .filter(String::isNotEmpty)
                                .map(String::toLong)
                        if (numbers.size > 10)
                            bandWidth = VpnBandwidth(numbers[0], numbers[8])
                        break
                    }
                }
                netDev.close()
                return bandWidth
            } catch (e: Exception) {
                e.printStackTrace()
                return null
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
                    val isRunning = vpnService?.isRunning!!
                            && VpnService.prepare(vpnService) == null
                    if (isRunning) {
                        MessageUtil.sendMsg2UI(vpnService!!, AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(vpnService!!, AppConfig.MSG_STATE_NOT_RUNNING, "")
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
                    vpnService?.startV2ray()
                }
            }
        }
    }

    fun handlePackets() {
        while (isRunning) {
            val n = inputStream?.read(buffer.array())
            n?.let { it } ?: return
            if (n > 0) {
                //Log.d(TAG, "handlePackets n:" + n.toString())
                buffer.limit(n)
                tun2socks.Tun2socks.inputPacket(buffer.array())
                buffer.clear()
            }
//            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
//                // In non-blocking mode
//                Thread.sleep(50)
//            }
        }
    }
}

