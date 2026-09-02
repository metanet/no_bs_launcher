package dev.basri.android.nobs_launcher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.FaviconRepository
import dev.basri.android.nobs_launcher.databinding.ItemAppSeparatorBinding
import dev.basri.android.nobs_launcher.databinding.ItemAppTileBinding
import dev.basri.android.nobs_launcher.model.HomeItem

sealed interface HomeGridItem {
    data class Tile(
        val item: HomeItem,
        val favorite: Boolean,
    ) : HomeGridItem

    data object Separator : HomeGridItem
}

class AppGridAdapter(
    private val favicons: FaviconRepository,
    private val onOpen: (HomeItem) -> Unit,
    private val onLongPress: (HomeItem, Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<HomeGridItem>()
    private var movingItemId: String? = null

    init {
        setHasStableIds(true)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitSections(
        favorites: List<HomeItem>,
        remaining: List<HomeItem>,
    ) {
        items.clear()
        items += favorites.map { HomeGridItem.Tile(it, favorite = true) }
        if (favorites.isNotEmpty() && remaining.isNotEmpty()) {
            items += HomeGridItem.Separator
        }
        items += remaining.map { HomeGridItem.Tile(it, favorite = false) }
        notifyDataSetChanged()
    }

    fun setMovingItemId(itemId: String?) {
        val previous = movingItemId
        movingItemId = itemId
        previous?.let(::notifyItemChangedById)
        itemId?.let(::notifyItemChangedById)
    }

    fun itemIdAt(position: Int): String? = (items.getOrNull(position) as? HomeGridItem.Tile)
        ?.item
        ?.id

    fun positionOfItem(itemId: String): Int = items.indexOfFirst { gridItem ->
        (gridItem as? HomeGridItem.Tile)?.item?.id == itemId
    }

    fun spanSizeAt(position: Int): Int = if (items.getOrNull(position) is HomeGridItem.Separator) {
        GRID_COLUMNS
    } else {
        1
    }

    override fun getItemId(position: Int): Long = when (val item = items[position]) {
        is HomeGridItem.Tile -> item.item.id.hashCode().toLong()
        HomeGridItem.Separator -> Long.MIN_VALUE
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeGridItem.Tile -> VIEW_TYPE_TILE
        HomeGridItem.Separator -> VIEW_TYPE_SEPARATOR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_TILE -> TileViewHolder(
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
            is TileViewHolder -> holder.bind(items[position] as HomeGridItem.Tile)
            is SeparatorViewHolder -> Unit
        }
    }

    override fun getItemCount(): Int = items.size

    private fun notifyItemChangedById(itemId: String) {
        val index = positionOfItem(itemId)
        if (index >= 0) notifyItemChanged(index)
    }

    inner class TileViewHolder(
        private val binding: ItemAppTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(gridItem: HomeGridItem.Tile) {
            val item = gridItem.item
            binding.appLabel.text = item.label
            when (item) {
                is HomeItem.App -> {
                    binding.appArtwork.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    binding.appArtwork.setImageDrawable(item.candidate.artwork)
                    if (item.candidate.artwork == null) {
                        binding.appArtwork.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
                is HomeItem.Web -> {
                    val favicon = favicons.load(item.shortcut.faviconFileName)
                    binding.appArtwork.scaleType = if (favicon == null) {
                        ImageView.ScaleType.CENTER_INSIDE
                    } else {
                        ImageView.ScaleType.FIT_CENTER
                    }
                    binding.appArtwork.setImageDrawable(favicon)
                    if (favicon == null) {
                        binding.appArtwork.setImageResource(R.drawable.ic_web_shortcut)
                    }
                }
            }
            binding.root.contentDescription = item.label
            binding.root.setBackgroundResource(
                if (item.id == movingItemId) R.drawable.tile_move else R.drawable.tile_selector,
            )
            binding.root.setOnClickListener { onOpen(item) }
            binding.root.setOnLongClickListener {
                onLongPress(item, gridItem.favorite)
                true
            }
        }
    }

    private class SeparatorViewHolder(
        binding: ItemAppSeparatorBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val VIEW_TYPE_TILE = 1
        const val VIEW_TYPE_SEPARATOR = 2
        const val GRID_COLUMNS = 4
    }
}
