package dev.basri.android.nobs_launcher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.FaviconRepository
import dev.basri.android.nobs_launcher.databinding.ItemWebShortcutBinding
import dev.basri.android.nobs_launcher.model.WebShortcut

class WebShortcutsAdapter(
    private val favicons: FaviconRepository,
    private val onEdit: (WebShortcut) -> Unit,
    private val onRemove: (WebShortcut) -> Unit,
) : RecyclerView.Adapter<WebShortcutsAdapter.ShortcutViewHolder>() {
    private val shortcuts = mutableListOf<WebShortcut>()

    init {
        setHasStableIds(true)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(shortcuts: List<WebShortcut>) {
        this.shortcuts.clear()
        this.shortcuts.addAll(shortcuts.sortedBy { it.name.lowercase() })
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = shortcuts[position].uuid.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder =
        ShortcutViewHolder(
            ItemWebShortcutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(shortcuts[position])
    }

    override fun getItemCount(): Int = shortcuts.size

    inner class ShortcutViewHolder(
        private val binding: ItemWebShortcutBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(shortcut: WebShortcut) {
            binding.shortcutLabel.text = shortcut.name
            binding.shortcutAddress.text = shortcut.url
            binding.root.contentDescription = shortcut.name
            val icon = favicons.load(shortcut.faviconFileName)
            binding.shortcutIcon.scaleType = if (icon == null) {
                ImageView.ScaleType.CENTER_INSIDE
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
            binding.shortcutIcon.setImageDrawable(icon)
            if (icon == null) binding.shortcutIcon.setImageResource(R.drawable.ic_web_shortcut)
            binding.shortcutEdit.setOnClickListener { onEdit(shortcut) }
            binding.shortcutRemove.setOnClickListener { onRemove(shortcut) }
        }
    }
}
