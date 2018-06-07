package com.v2ray.ang.util

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.util.Base64
import com.google.zxing.WriterException
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.EncodeHintType
import java.util.*
import kotlin.collections.HashMap
import android.app.ActivityManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Patterns
import android.webkit.URLUtil
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.service.V2RayVpnService
import com.v2ray.ang.ui.SettingsActivity
import me.dozen.dpreference.DPreference
import org.jetbrains.anko.toast

object Utils {

    /**
     * convert string to editalbe for kotlin
     *
     * @param text
     * @return
     */
    fun getEditable(text: String): Editable {
        return Editable.Factory.getInstance().newEditable(text)
    }

    /**
     * find value in array position
     */
    fun arrayFind(array: Array<out String>, value: String): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return -1
    }

    /**
     * parseInt
     */
    fun parseInt(str: String): Int {
        try {
            return Integer.parseInt(str)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    /**
     * get text from clipboard
     */
    fun getClipboard(context: Context): String {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            return cmb.primaryClip?.getItemAt(0)?.text.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * set text to clipboard
     */
    fun setClipboard(context: Context, content: String) {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(null, content)
            cmb.primaryClip = clipData
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * base64 decode
     */
    fun decode(text: String): String {
        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(charset("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * base64 encode
     */
    fun encode(text: String): String {
        try {
            return Base64.encodeToString(text.toByteArray(charset("UTF-8")), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * get remote dns servers from preference
     */
    fun getRemoteDnsServers(defaultDPreference: DPreference): List<String> {
        val remoteDns = defaultDPreference.getPrefString(SettingsActivity.PREF_REMOTE_DNS, "")
        val ret = ArrayList<String>()
        if (!TextUtils.isEmpty(remoteDns)) {
            remoteDns
                    .split(",")
                    .forEach {
                        if (Utils.isIpAddress(it)) {
                            ret.add(it)
                        }
                    }
        }

        if (ret.size <= 0) {
            if (!ret.contains("8.8.8.8")) {
                ret.add("8.8.8.8")
            }
            if (!ret.contains("8.8.4.4")) {
                ret.add("8.8.4.4")
            }
        }
//        if (!ret.contains("localhost")) {
//            ret.add("localhost")
//        }
        return ret
    }

    /**
     * create qrcode using zxing
     */
    fun createQRCode(text: String, size: Int = 800): Bitmap? {
        try {
            val hints = HashMap<EncodeHintType, String>()
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8")
            val bitMatrix = QRCodeWriter().encode(text,
                    BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0..size - 1) {
                for (x in 0..size - 1) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * size + x] = 0xff000000.toInt()
                    } else {
                        pixels[y * size + x] = 0xffffffff.toInt()
                    }

                }
            }
            val bitmap = Bitmap.createBitmap(size, size,
                    Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * is ip address
     */
    fun isIpAddress(value: String): Boolean {
        try {
            var start = 0
            var end = value.indexOf('.')
            var numBlocks = 0

            while (start < value.length) {

                if (end == -1) {
                    end = value.length
                }

                try {
                    val block = Integer.parseInt(value.substring(start, end))
                    if (block > 255 || block < 0) {
                        return false
                    }
                } catch (e: NumberFormatException) {
                    return false
                }

                numBlocks++
                start = end + 1
                end = value.indexOf('.', start)
            }
            return numBlocks == 4
        } catch (e: WriterException) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * is valid url
     */
    fun isValidUrl(value: String?): Boolean {
        try {
            if (Patterns.WEB_URL.matcher(value).matches() || URLUtil.isValidUrl(value)) {
                return true
            }
        } catch (e: WriterException) {
            e.printStackTrace()
            return false
        }
        return false
    }


    /**
     * 判断服务是否后台运行

     * @param context
     * *            Context
     * *
     * @param className
     * *            判断的服务名字
     * *
     * @return true 在运行 false 不在运行
     */
    fun isServiceRun(context: Context, className: String): Boolean {
        var isRun = false
        val activityManager = context
                .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceList = activityManager
                .getRunningServices(999)
        val size = serviceList.size
        for (i in 0..size - 1) {
            if (serviceList[i].service.className == className) {
                isRun = true
                break
            }
        }
        return isRun
    }

    /**
     * startVService
     */
    fun startVService(context: Context): Boolean {
        context.toast(R.string.toast_services_start)
        if (AngConfigManager.genStoreV2rayConfig()) {
            V2RayVpnService.startV2Ray(context)
            return true
        } else {
            return false
        }
    }

    /**
     * startVService
     */
    fun startVService(context: Context, guid: String): Boolean {
        val index = AngConfigManager.getIndexViaGuid(guid)
        return startVService(context, index)
    }

    /**
     * startVService
     */
    fun startVService(context: Context, index: Int): Boolean {
        AngConfigManager.setActiveServer(index)
        return startVService(context)
    }

    /**
     * stopVService
     */
    fun stopVService(context: Context) {
        context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    fun openUri(context: Context, uriString: String) {
        val uri = Uri.parse(uriString)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}


