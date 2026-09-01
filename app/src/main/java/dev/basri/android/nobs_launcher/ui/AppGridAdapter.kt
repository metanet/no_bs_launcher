package dev.basri.android.nobs_launcher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.databinding.ItemAppSeparatorBinding
import dev.basri.android.nobs_launcher.databinding.ItemAppTileBinding

sealed interface HomeGridItem {
    data class App(
        val candidate: AppCandidate,
        val favorite: Boolean,
    ) : HomeGridItem

    data object Separator : HomeGridItem
}

class AppGridAdapter(
    private val onOpen: (AppCandidate) -> Unit,
    private val onLongPress: (AppCandidate, Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<HomeGridItem>()
    private var movingPackage: String? = null

    init {
        setHasStableIds(true)
    }

    fun submitSections(
        favorites: List<AppCandidate>,
        remaining: List<AppCandidate>,
    ) {
        items.clear()
        items += favorites.map { HomeGridItem.App(it, favorite = true) }
        if (favorites.isNotEmpty() && remaining.isNotEmpty()) {
            items += HomeGridItem.Separator
        }
        items += remaining.map { HomeGridItem.App(it, favorite = false) }
        notifyDataSetChanged()
    }

    fun setMovingPackage(packageName: String?) {
        val previous = movingPackage
        movingPackage = packageName
        previous?.let(::notifyPackageChanged)
        packageName?.let(::notifyPackageChanged)
    }

    fun packageAt(position: Int): String? = (items.getOrNull(position) as? HomeGridItem.App)
        ?.candidate
        ?.packageName

    fun positionOfPackage(packageName: String): Int = items.indexOfFirst { item ->
        (item as? HomeGridItem.App)?.candidate?.packageName == packageName
    }

    fun spanSizeAt(position: Int): Int = if (items.getOrNull(position) is HomeGridItem.Separator) {
        GRID_COLUMNS
    } else {
        1
    }

    override fun getItemId(position: Int): Long = when (val item = items[position]) {
        is HomeGridItem.App -> item.candidate.packageName.hashCode().toLong()
        HomeGridItem.Separator -> Long.MIN_VALUE
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeGridItem.App -> VIEW_TYPE_APP
        HomeGridItem.Separator -> VIEW_TYPE_SEPARATOR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_APP -> AppViewHolder(
                ItemAppTileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
            )
            VIEW_TYPE_SEPARATOR -> SeparatorViewHolder(
                ItemAppSeparatorBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
            )
            else -> error("Unknown Home grid view type: $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AppViewHolder -> holder.bind(items[position] as HomeGridItem.App)
            is SeparatorViewHolder -> Unit
        }
    }

    override fun getItemCount(): Int = items.size

    private fun notifyPackageChanged(packageName: String) {
        val index = positionOfPackage(packageName)
        if (index >= 0) notifyItemChanged(index)
    }

    inner class AppViewHolder(
        private val binding: ItemAppTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HomeGridItem.App) {
            val app = item.candidate
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
                onLongPress(app, item.favorite)
                true
            }
        }
    }

    private class SeparatorViewHolder(
        binding: ItemAppSeparatorBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val VIEW_TYPE_APP = 1
        const val VIEW_TYPE_SEPARATOR = 2
        const val GRID_COLUMNS = 4
    }
}
