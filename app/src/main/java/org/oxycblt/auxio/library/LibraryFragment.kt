/*
 * Copyright (c) 2021 Auxio Project
 * LibraryFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentLibraryBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.logD
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Parent
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.recycler.sliceArticle
import org.oxycblt.auxio.spans
import org.oxycblt.auxio.ui.newMenu

/**
 * A [Fragment] that shows a custom list of [Genre], [Artist], or [Album] data. Also allows for
 * search functionality.
 * @author OxygenCobalt
 */
class LibraryFragment : Fragment() {
    private val libraryModel: LibraryViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentLibraryBinding.inflate(inflater)
        val libraryAdapter = LibraryAdapter(::navToDetail, ::newMenu)

        // --- UI SETUP ---

        binding.lifecycleOwner = viewLifecycleOwner

        binding.libraryToolbar.apply {
            menu.findItem(libraryModel.sortMode.toMenuId()).isChecked = true

            setOnMenuItemClickListener { item ->
                if (item.itemId != R.id.submenu_sorting) {
                    libraryModel.updateSortMode(item.itemId)
                    item.isChecked = true
                    true
                } else {
                    false
                }
            }
        }

        binding.libraryRecycler.apply {
            adapter = libraryAdapter
            setHasFixedSize(true)

            if (spans != 1) {
                layoutManager = GridLayoutManager(requireContext(), spans)
            }
        }

        binding.libraryFastScroll.setup(binding.libraryRecycler) { pos ->
            val item = libraryModel.libraryData.value!![pos]
            val char = item.displayName.sliceArticle().first().uppercaseChar()

            if (char.isDigit()) '#' else char
        }

        // --- VIEWMODEL SETUP ---

        libraryModel.libraryData.observe(viewLifecycleOwner) { data ->
            libraryAdapter.updateData(data)
        }

        detailModel.navToItem.observe(viewLifecycleOwner) { item ->
            if (item != null) {
                libraryModel.setNavigating(false)

                if (item is Parent) {
                    navToDetail(item)
                } else if (item is Song) {
                    navToDetail(item.album)
                }
            }
        }

        logD("Fragment created.")

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        libraryModel.setNavigating(false)
    }

    /**
     * Navigate to the detail UI for a [parent].
     */
    private fun navToDetail(parent: Parent) {
        requireView().rootView.clearFocus()

        if (!libraryModel.isNavigating) {
            libraryModel.setNavigating(true)

            logD("Navigating to the detail fragment for ${parent.name}")

            findNavController().navigate(
                when (parent) {
                    is Genre -> LibraryFragmentDirections.actionShowGenre(parent.id)
                    is Artist -> LibraryFragmentDirections.actionShowArtist(parent.id)
                    is Album -> LibraryFragmentDirections.actionShowAlbum(parent.id)
                }
            )
        }
    }
}
