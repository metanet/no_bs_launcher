package dev.basri.android.nobs_launcher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.databinding.ItemAppTileBinding

class AppGridAdapter(
    private val onOpen: (AppCandidate) -> Unit,
    private val onStartMoving: (AppCandidate) -> Unit,
) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {
    private val apps = mutableListOf<AppCandidate>()
    private var movingPackage: String? = null

    init {
        setHasStableIds(true)
    }

    fun submitApps(updatedApps: List<AppCandidate>) {
        val previousSize = apps.size
        apps.clear()
        apps.addAll(updatedApps)
        if (previousSize > 0) notifyItemRangeRemoved(0, previousSize)
        if (apps.isNotEmpty()) notifyItemRangeInserted(0, apps.size)
    }

    fun setMovingPackage(packageName: String?) {
        val previous = movingPackage
        movingPackage = packageName
        previous?.let(::notifyPackageChanged)
        packageName?.let(::notifyPackageChanged)
    }

    fun packageAt(position: Int): String? = apps.getOrNull(position)?.packageName

    override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppTileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    private fun notifyPackageChanged(packageName: String) {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index >= 0) notifyItemChanged(index)
    }

    inner class AppViewHolder(
        private val binding: ItemAppTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppCandidate) {
            binding.appLabel.text = app.label
            binding.appArtwork.setImageDrawable(app.artwork)
            if (app.artwork == null) {
                binding.appArtwork.setImageResource(R.drawable.ic_launcher_foreground)
            }
            binding.root.contentDescription = app.label
            binding.root.setBackgroundResource(
                if (app.packageName == movingPackage) {
                    R.drawable.tile_move
                } else {
                    R.drawable.tile_selector
                },
            )
            binding.root.setOnClickListener { onOpen(app) }
            binding.root.setOnLongClickListener {
                onStartMoving(app)
                true
            }
        }
    }
}
