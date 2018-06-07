package com.v2ray.ang.util

import android.os.Build
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.putOpt
import com.v2ray.ang.ui.SettingsActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.logging.Logger

object V2rayConfigUtil {
    private val lib2rayObj: JSONObject by lazy {
        JSONObject("""{
                    "enabled": true,
                    "listener": {
                    "onUp": "#none",
                    "onDown": "#none"
                    },
                    "env": [
                    "V2RaySocksPort=10808"
                    ],
                    "render": [],
                    "escort": [],
                    "vpnservice": {
                    "Target": "${"$"}{datadir}tun2socks",
                    "Args": [
                    "--netif-ipaddr",
                    "26.26.26.2",
                    "--netif-netmask",
                    "255.255.255.0",
                    "--socks-server-addr",
                    "127.0.0.1:${"$"}V2RaySocksPort",
                    "--tunfd",
                    "3",
                    "--tunmtu",
                    "1500",
                    "--sock-path",
                    "/dev/null",
                    "--loglevel",
                    "4",
                    "--enable-udprelay"
                    ],
                    "VPNSetupArg": "m,1500 a,26.26.26.1,24 r,0.0.0.0,0"
                    },
                    "preparedDomainName": {
                      "domainName": [
                      ],
                      "tcpVersion": "tcp4",
                      "udpVersion": "udp4"
                    }
                }""")
    }

    private val requestObj: JSONObject by lazy {
        JSONObject("""{"version":"1.1","method":"GET","path":["/"],"headers":{"User-Agent":["Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.75 Safari/537.36","Mozilla/5.0 (iPhone; CPU iPhone OS 10_0_2 like Mac OS X) AppleWebKit/601.1 (KHTML, like Gecko) CriOS/53.0.2785.109 Mobile/14A456 Safari/601.1.46"],"Accept-Encoding":["gzip, deflate"],"Connection":["keep-alive"],"Pragma":"no-cache"}}""")
    }
    private val responseObj: JSONObject by lazy {
        JSONObject("""{"version":"1.1","status":"200","reason":"OK","headers":{"Content-Type":["application/octet-stream","video/mpeg"],"Transfer-Encoding":["chunked"],"Connection":["keep-alive"],"Pragma":"no-cache"}}""")
    }

    private val replacementPairs by lazy {
        mapOf("port" to 10808,
                "inbound" to JSONObject("""{
                    "protocol": "socks",
                    "listen": "127.0.0.1",
                    "settings": {
                        "auth": "noauth",
                        "udp": true
                    },
                    "domainOverride": ["http", "tls"]
                }"""),
                "inboundDetour" to JSONArray(),
                "#lib2ray" to lib2rayObj,
                "log" to JSONObject("""{
                    "loglevel": "warning"
                }""")
        )
    }
    private val ruleDirectDnsObj: JSONObject by lazy {
        JSONObject("""{"type":"field","port":53,"network":"udp","outboundTag":"direct"}""")
    }

    data class Result(var status: Boolean, var content: String)

    /**
     * 生成v2ray的客户端配置文件
     */
    fun getV2rayConfig(app: AngApplication, config: AngConfig): Result {
        var result = Result(false, "")
        try {
            //检查设置
            if (config.index < 0
                    || config.vmess.count() <= 0
                    || config.index > config.vmess.count() - 1
            ) {
                return result
            }

            if (config.vmess[config.index].configType == 1) {
                result = getV2rayConfigType1(app, config)
            } else if (config.vmess[config.index].configType == 2) {
                result = getV2rayConfigType2(app, config)
            }
            Log.d("V2rayConfigUtil", result.content)
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return result
        }
    }

    /**
     * 生成v2ray的客户端配置文件
     */
    private fun getV2rayConfigType1(app: AngApplication, config: AngConfig): Result {
        val result = Result(false, "")
        try {
            //检查设置
            if (config.index < 0
                    || config.vmess.count() <= 0
                    || config.index > config.vmess.count() - 1
            ) {
                return result
            }

            //取得默认配置
            val assets = AssetsUtil.readTextFromAssets(app.assets, "v2ray_config.json")
            if (TextUtils.isEmpty(assets)) {
                return result
            }

            //转成Json
            val v2rayConfig = Gson().fromJson(assets, V2rayConfig::class.java) ?: return result
//            if (v2rayConfig == null) {
//                return result
//            }

            //vmess协议服务器配置
            outbound(config, v2rayConfig, app)

            //routing
            routing(config, v2rayConfig, app)

            //dns
            customDns(config, v2rayConfig, app)

            //增加lib2ray
            val finalConfig = addLib2ray(v2rayConfig, app)

            Log.d("config", finalConfig)

            result.status = true
            result.content = finalConfig
            return result

        } catch (e: Exception) {
            e.printStackTrace()
            return result
        }
    }

    /**
     * 生成v2ray的客户端配置文件
     */
    private fun getV2rayConfigType2(app: AngApplication, config: AngConfig): Result {
        val result = Result(false, "")
        try {
            //检查设置
            if (config.index < 0
                    || config.vmess.count() <= 0
                    || config.index > config.vmess.count() - 1
            ) {
                return result
            }
            val vmess = config.vmess[config.index]
            val guid = vmess.guid
            val jsonConfig = app.defaultDPreference.getPrefString(AppConfig.ANG_CONFIG + guid, "")

            //增加lib2ray
            val finalConfig = addLib2ray2(jsonConfig)

            result.status = true
            result.content = finalConfig
            return result

        } catch (e: Exception) {
            e.printStackTrace()
            return result
        }
    }

    /**
     * vmess协议服务器配置
     */
    private fun outbound(config: AngConfig, v2rayConfig: V2rayConfig, app: AngApplication): Boolean {
        try {
            val vmess = config.vmess[config.index]
            v2rayConfig.outbound.settings.vnext[0].address = vmess.address
            v2rayConfig.outbound.settings.vnext[0].port = vmess.port

            v2rayConfig.outbound.settings.vnext[0].users[0].id = vmess.id
            v2rayConfig.outbound.settings.vnext[0].users[0].alterId = vmess.alterId
            v2rayConfig.outbound.settings.vnext[0].users[0].security = vmess.security

            //Mux
            val muxEnabled = app.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_MUX_ENABLED, false)
            v2rayConfig.outbound.mux.enabled = muxEnabled

            //远程服务器底层传输配置
            v2rayConfig.outbound.streamSettings = boundStreamSettings(config)

            //如果非ip
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (!Utils.isIpAddress(vmess.address)) {
                    val addr = String.format("%s:%s", vmess.address, vmess.port)
                    val domainName = lib2rayObj.optJSONObject("preparedDomainName")
                            .optJSONArray("domainName")
                    if (domainName.length() > 0) {
                        for (index in 0 until domainName.length()) {
                            domainName.remove(index)
                        }
                    }
                    domainName.put(addr)
                }
            } else {
                if (!Utils.isIpAddress(vmess.address)) {
                    lib2rayObj.optJSONObject("preparedDomainName")
                            .optJSONArray("domainName")
                            .put(String.format("%s:%s", vmess.address, vmess.port))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * 远程服务器底层传输配置
     */
    private fun boundStreamSettings(config: AngConfig): V2rayConfig.OutboundBean.StreamSettingsBean {
        val streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean("", "", null, null, null, null, null)
        try {
            //远程服务器底层传输配置
            streamSettings.network = config.vmess[config.index].network
            streamSettings.security = config.vmess[config.index].streamSecurity

            //streamSettings
            when (streamSettings.network) {
                "kcp" -> {
                    val kcpsettings = V2rayConfig.OutboundBean.StreamSettingsBean.KcpsettingsBean()
                    kcpsettings.mtu = 1350
                    kcpsettings.tti = 50
                    kcpsettings.uplinkCapacity = 12
                    kcpsettings.downlinkCapacity = 100
                    kcpsettings.congestion = false
                    kcpsettings.readBufferSize = 1
                    kcpsettings.writeBufferSize = 1
                    kcpsettings.header = V2rayConfig.OutboundBean.StreamSettingsBean.KcpsettingsBean.HeaderBean()
                    kcpsettings.header.type = config.vmess[config.index].headerType
                    streamSettings.kcpsettings = kcpsettings
                }
                "ws" -> {
                    val wssettings = V2rayConfig.OutboundBean.StreamSettingsBean.WssettingsBean()
                    wssettings.connectionReuse = true
                    val host = config.vmess[config.index].requestHost.trim()
                    val path = config.vmess[config.index].path.trim()

                    if (!TextUtils.isEmpty(host)) {
                        wssettings.headers = V2rayConfig.OutboundBean.StreamSettingsBean.WssettingsBean.HeadersBean()
                        wssettings.headers.Host = host
                    }
                    if (!TextUtils.isEmpty(path)) {
                        wssettings.path = path
                    }
                    streamSettings.wssettings = wssettings

                    val tlssettings = V2rayConfig.OutboundBean.StreamSettingsBean.TlssettingsBean()
                    tlssettings.allowInsecure = true
                    streamSettings.tlssettings = tlssettings
                }
                "h2" -> {
                    val httpsettings = V2rayConfig.OutboundBean.StreamSettingsBean.HttpsettingsBean()
                    val host = config.vmess[config.index].requestHost.trim()
                    val path = config.vmess[config.index].path.trim()

                    if (!TextUtils.isEmpty(host)) {
                        httpsettings.host = host.split(",").map { it.trim() }
                    }
                    httpsettings.path = path
                    streamSettings.httpsettings = httpsettings

                    val tlssettings = V2rayConfig.OutboundBean.StreamSettingsBean.TlssettingsBean()
                    tlssettings.allowInsecure = true
                    streamSettings.tlssettings = tlssettings
                }
                else -> {
                    //tcp带http伪装
                    if (config.vmess[config.index].headerType == "http") {
                        val tcpSettings = V2rayConfig.OutboundBean.StreamSettingsBean.TcpsettingsBean()
                        tcpSettings.connectionReuse = true
                        tcpSettings.header = V2rayConfig.OutboundBean.StreamSettingsBean.TcpsettingsBean.HeaderBean()
                        tcpSettings.header.type = config.vmess[config.index].headerType

                        if (requestObj.has("headers")
                                || requestObj.optJSONObject("headers").has("Pragma")) {
                            val arrHost = JSONArray()
                            config.vmess[config.index].requestHost
                                    .split(",")
                                    .forEach {
                                        arrHost.put(it)
                                    }
                            requestObj.optJSONObject("headers")
                                    .put("Host", arrHost)
                            tcpSettings.header.request = requestObj
                            tcpSettings.header.response = responseObj
                        }
                        streamSettings.tcpSettings = tcpSettings
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return streamSettings
        }
        return streamSettings
    }

    /**
     * routing
     */
    private fun routing(config: AngConfig, v2rayConfig: V2rayConfig, app: AngApplication): Boolean {
        try {
            routingUserRule(app.defaultDPreference.getPrefString(AppConfig.PREF_V2RAY_ROUTING_AGENT, ""), AppConfig.TAG_AGENT, v2rayConfig)
            routingUserRule(app.defaultDPreference.getPrefString(AppConfig.PREF_V2RAY_ROUTING_DIRECT, ""), AppConfig.TAG_DIRECT, v2rayConfig)
            routingUserRule(app.defaultDPreference.getPrefString(AppConfig.PREF_V2RAY_ROUTING_BLOCKED, ""), AppConfig.TAG_BLOCKED, v2rayConfig)

            val routingMode = app.defaultDPreference.getPrefString(SettingsActivity.PREF_ROUTING_MODE, "0")
            when (routingMode) {
                "0" -> {
                }
                "1" -> {
                    routingGeo("", "cn", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "custom:direct", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "surge:direct", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("ip", "private", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("domain", "gfwlist:direct", AppConfig.TAG_DIRECT, v2rayConfig)
                }
                "2" -> {
                    routingGeo("", "custom:reject", AppConfig.TAG_BLOCKED, v2rayConfig)
                    routingGeo("", "surge:reject", AppConfig.TAG_BLOCKED, v2rayConfig)

                    routingGeo("", "cn", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "custom:direct", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "surge:direct", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("ip", "private", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("domain", "gfwlist:direct", AppConfig.TAG_DIRECT, v2rayConfig)

                    routingGeo("", "custom:proxy", AppConfig.TAG_AGENT, v2rayConfig)
                    routingGeo("", "surge:proxy", AppConfig.TAG_AGENT, v2rayConfig)
                    routingGeo("domain", "gfwlist:proxy", AppConfig.TAG_AGENT, v2rayConfig)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun routingGeo(ipOrDomain: String, code: String, tag: String, v2rayConfig: V2rayConfig) {
        try {
            if (!TextUtils.isEmpty(code)) {
                //IP
                if (ipOrDomain == "ip" || ipOrDomain == "") {
                    val rulesIP = V2rayConfig.RoutingBean.SettingsBean.RulesBean("", null, null, "")
                    rulesIP.type = "field"
                    rulesIP.outboundTag = tag
                    rulesIP.ip = ArrayList<String>()
                    rulesIP.ip?.add("geoip:$code")
                    v2rayConfig.routing.settings.rules.add(rulesIP)
                }

                if (ipOrDomain == "domain" || ipOrDomain == "") {
                    //Domain
                    val rulesDomain = V2rayConfig.RoutingBean.SettingsBean.RulesBean("", null, null, "")
                    rulesDomain.type = "field"
                    rulesDomain.outboundTag = tag
                    rulesDomain.domain = ArrayList<String>()
                    rulesDomain.domain?.add("geosite:$code")
                    v2rayConfig.routing.settings.rules.add(rulesDomain)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun routingUserRule(userRule: String, tag: String, v2rayConfig: V2rayConfig) {
        try {
            if (!TextUtils.isEmpty(userRule)) {
                //Domain
                val rulesDomain = V2rayConfig.RoutingBean.SettingsBean.RulesBean("", null, null, "")
                rulesDomain.type = "field"
                rulesDomain.outboundTag = tag
                rulesDomain.domain = ArrayList<String>()

                //IP
                val rulesIP = V2rayConfig.RoutingBean.SettingsBean.RulesBean("", null, null, "")
                rulesIP.type = "field"
                rulesIP.outboundTag = tag
                rulesIP.ip = ArrayList<String>()

                userRule
                        .split(",")
                        .forEach {
                            if (Utils.isIpAddress(it) || it.startsWith("geoip:")) {
                                rulesIP.ip?.add(it)
                            } else if (Utils.isValidUrl(it)
                                    || it.startsWith("geosite:")
                                    || it.startsWith("regexp:")
                                    || it.startsWith("domain:")) {
                                rulesDomain.domain?.add(it)
                            }
                        }
                if (rulesDomain.domain?.size!! > 0) {
                    v2rayConfig.routing.settings.rules.add(rulesDomain)
                }
                if (rulesIP.ip?.size!! > 0) {
                    v2rayConfig.routing.settings.rules.add(rulesIP)
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Custom Dns
     */
    private fun customDns(config: AngConfig, v2rayConfig: V2rayConfig, app: AngApplication): Boolean {
        try {
            v2rayConfig.dns.servers = Utils.getRemoteDnsServers(app.defaultDPreference)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }


    /**
     * 增加lib2ray
     */
    private fun addLib2ray(v2rayConfig: V2rayConfig, app: AngApplication): String {
        try {
            val conf = Gson().toJson(v2rayConfig)
            val jObj = JSONObject(conf)
            jObj.put("#lib2ray", lib2rayObj)

//            val speedupDomain = app.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_SPEEDUP_DOMAIN, false)
//            if (speedupDomain) {
//                jObj.optJSONObject("routing")
//                        .optJSONObject("settings")
//                        .optJSONArray("rules")
//                        .put(0, ruleDirectDnsObj)
//            }

            return jObj.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * 增加lib2ray
     */
    private fun addLib2ray2(jsonConfig: String): String {
        try {
            val jObj = JSONObject(jsonConfig)
            //find outbound address and port
            try {
                if (jObj.has("outbound")
                        || jObj.optJSONObject("outbound").has("settings")
                        || jObj.optJSONObject("outbound").optJSONObject("settings").has("vnext")) {
                    val vnext = jObj.optJSONObject("outbound").optJSONObject("settings").optJSONArray("vnext")
                    for (i in 0..(vnext.length() - 1)) {
                        val item = vnext.getJSONObject(i)
                        val address = item.getString("address")
                        val port = item.getString("port")
                        if (!Utils.isIpAddress(address)) {
                            lib2rayObj.optJSONObject("preparedDomainName")
                                    .optJSONArray("domainName")
                                    .put(String.format("%s:%s", address, port))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            jObj.putOpt(replacementPairs)
            return jObj.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * is valid config
     */
    fun isValidConfig(conf: String): Boolean {
        try {
            val jObj = JSONObject(conf)
            return jObj.has("outbound") and jObj.has("inbound")
        } catch (e: JSONException) {
            return false
        }
    }
}