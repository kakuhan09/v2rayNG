package com.v2ray.ang.ui

import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.v2ray.ang.R
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_qrcode.view.*
import kotlinx.android.synthetic.main.item_recycler_main.view.*
import org.jetbrains.anko.*
import java.util.*

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>()
        , ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var mActivity: MainActivity = activity
    private lateinit var configs: AngConfig
    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_method)
    }

    var changeable: Boolean = true
        set(value) {
            if (field == value)
                return
            field = value
            notifyDataSetChanged()
        }

    init {
        updateConfigList()
    }

    override fun getItemCount() = configs.vmess.count() + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val configType = configs.vmess[position].configType
            val remarks = configs.vmess[position].remarks
            val guid = configs.vmess[position].guid
            val address = configs.vmess[position].address
            val port = configs.vmess[position].port

            holder.name.text = remarks
            holder.radio.isChecked = (position == configs.index)
            holder.itemView.backgroundColor = Color.TRANSPARENT

            if (configType == 1) {
                holder.statistics.text = "$address : $port"
                holder.layout_share.visibility = View.VISIBLE
            } else if (configType == 2) {
                holder.statistics.text = mActivity.getString(R.string.server_customize_config)
                holder.layout_share.visibility = View.INVISIBLE
            }

            holder.layout_share.setOnClickListener {
                mActivity.selector(null, share_method.asList()) { dialogInterface, i ->
                    try {
                        when (i) {
                            0 -> {
                                val iv = mActivity.layoutInflater.inflate(R.layout.item_qrcode, null)
                                iv.iv_qcode.setImageBitmap(AngConfigManager.share2QRCode(position))

                                mActivity.alert {
                                    customView {
                                        linearLayout {
                                            addView(iv)
                                        }
                                    }
                                }.show()
                            }
                            1 -> {
                                if (AngConfigManager.share2Clipboard(position) == 0) {
                                    mActivity.toast(R.string.toast_success)
                                } else {
                                    mActivity.toast(R.string.toast_failure)
                                }
                            }
                            else ->
                                mActivity.toast("else")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            holder.layout_edit.setOnClickListener {
                if (configType == 1) {
                    mActivity.startActivity<ServerActivity>("position" to position, "isRunning" to !changeable)
                } else if (configType == 2) {
                    mActivity.startActivity<Server2Activity>("position" to position, "isRunning" to !changeable)
                }
            }

            holder.infoContainer.setOnClickListener {
                if (changeable) {
                    AngConfigManager.setActiveServer(position)
                } else {
                    AngConfigManager.setActiveServer(position)
                    mActivity.showCircle()
                    Utils.startVService(mActivity)

                }
                notifyDataSetChanged()
            }
        } else {
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        when (viewType) {
            VIEW_TYPE_ITEM ->
                return MainViewHolder(parent.context.layoutInflater
                        .inflate(R.layout.item_recycler_main, parent, false))
            else ->
                return FooterViewHolder(parent.context.layoutInflater
                        .inflate(R.layout.item_recycler_footer, parent, false))
        }
    }

    fun updateConfigList() {
        configs = AngConfigManager.configs
        notifyDataSetChanged()
    }

    fun updateSelectedItem() {
        notifyItemChanged(configs.index)
    }

    override fun getItemViewType(position: Int): Int {
        if (position == configs.vmess.count()) {
            return VIEW_TYPE_FOOTER
        } else {
            return VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(itemView: View) : BaseViewHolder(itemView), ItemTouchHelperViewHolder {
        val radio = itemView.btn_radio!!
        val name = itemView.tv_name!!
        val statistics = itemView.tv_statistics!!
        val infoContainer = itemView.info_container!!
        val layout_edit = itemView.layout_edit!!
        val layout_share = itemView.layout_share!!

        override fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        override fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class FooterViewHolder(itemView: View) : BaseViewHolder(itemView)

    override fun onItemDismiss(position: Int) {
        if (configs.index != position) {
            if (AngConfigManager.removeServer(position) == 0) {
                notifyItemRemoved(position)
            }
        }
        notifyItemChanged(position)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        AngConfigManager.swapServer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }
}
