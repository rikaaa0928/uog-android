package moe.rikaaa0928.uot

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConfigAdapter(
    private var configList: MutableList<Config>,
    private val onItemClick: (Int) -> Unit,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {

    private var activePosition = -1

    fun setActivePosition(position: Int) {
        activePosition = position
        notifyDataSetChanged()
    }

    fun updateConfigList(newList: MutableList<Config>) {
        configList = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewConfig: TextView = view.findViewById(R.id.textViewConfig)
        val buttonEdit: Button = view.findViewById(R.id.buttonEdit)
        val buttonDelete: Button = view.findViewById(R.id.buttonDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configList[position]
        holder.textViewConfig.text = "端口: ${config.listenPort}, 端点: ${config.grpcEndpoint}"
        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.buttonEdit.setOnClickListener { onEditClick(position) }
        holder.buttonDelete.setOnClickListener { onDeleteClick(position) }

        if (position == activePosition) {
            holder.itemView.setBackgroundColor(Color.LTGRAY)
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE)
        }
    }

    override fun getItemCount() = configList.size
}
