package dev.basri.android.nobs_launcher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.databinding.ItemManageAppBinding

class ManageAppsAdapter(
    private val onVisibilityChanged: (AppCandidate, Boolean) -> Unit,
) : RecyclerView.Adapter<ManageAppsAdapter.AppViewHolder>() {
    private val apps = mutableListOf<AppCandidate>()
    private val selectedPackages = mutableSetOf<String>()

    fun submit(apps: List<AppCandidate>, selectedPackages: Collection<String>) {
        val previousSize = this.apps.size
        this.apps.clear()
        this.apps.addAll(apps)
        this.selectedPackages.clear()
        this.selectedPackages.addAll(selectedPackages)
        if (previousSize > 0) notifyItemRangeRemoved(0, previousSize)
        if (this.apps.isNotEmpty()) notifyItemRangeInserted(0, this.apps.size)
    }

    fun updateSelection(selectedPackages: Collection<String>) {
        val previousSelection = this.selectedPackages.toSet()
        this.selectedPackages.clear()
        this.selectedPackages.addAll(selectedPackages)
        apps.forEachIndexed { index, app ->
            if ((app.packageName in previousSelection) != (app.packageName in this.selectedPackages)) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemManageAppBinding.inflate(
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

    inner class AppViewHolder(
        private val binding: ItemManageAppBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppCandidate) {
            binding.manageAppLabel.text = app.label
            binding.manageAppSelected.isChecked = app.packageName in selectedPackages
            binding.manageAppArtwork.setImageDrawable(app.artwork)
            if (app.artwork == null) {
                binding.manageAppArtwork.setImageResource(R.drawable.ic_launcher_foreground)
            }
            binding.root.contentDescription = app.label
            binding.root.setOnClickListener {
                val visible = app.packageName !in selectedPackages
                onVisibilityChanged(app, visible)
            }
        }
    }
}
