/*
 * Copyright (c) 2021 Auxio Project
 * BlacklistDialog.kt is part of Auxio.
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

package org.oxycblt.auxio.excluded

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import org.oxycblt.auxio.MainActivity
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogExcludedBinding
import org.oxycblt.auxio.logD
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.settings.ui.LifecycleDialog
import org.oxycblt.auxio.showToast
import kotlin.system.exitProcess

/**
 * Dialog that manages the currently excluded directories.
 * @author OxygenCobalt
 */
class ExcludedDialog : LifecycleDialog() {
    private val blacklistModel: ExcludedViewModel by viewModels {
        ExcludedViewModel.Factory(requireContext())
    }

    private val playbackModel: PlaybackViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogExcludedBinding.inflate(inflater)

        val adapter = ExcludedEntryAdapter { path ->
            blacklistModel.removePath(path)
        }

        val launcher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(), ::addDocTreePath
        )

        // --- UI SETUP ---

        binding.excludedRecycler.adapter = adapter

        // Now that the dialog exists, we get the view manually when the dialog is shown
        // and override its click-listener so that the dialog does not auto-dismiss when we
        // click the "Add"/"Save" buttons. This prevents the dialog from disappearing in the former
        // and the app from crashing in the latter.
        val dialog = requireDialog() as AlertDialog

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                launcher.launch(null)
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (blacklistModel.isModified()) {
                    saveAndRestart()
                } else {
                    dismiss()
                }
            }
        }

        // --- VIEWMODEL SETUP ---

        blacklistModel.paths.observe(viewLifecycleOwner) { paths ->
            adapter.submitList(paths)

            binding.excludedEmpty.isVisible = paths.isEmpty()
        }

        logD("Dialog created.")

        return binding.root
    }

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.set_excluded)

        // Dont set the click listener here, we do some custom black magic in onCreateView instead.
        builder.setNeutralButton(R.string.lbl_add, null)
        builder.setPositiveButton(R.string.lbl_save, null)
        builder.setNegativeButton(android.R.string.cancel, null)
    }

    private fun addDocTreePath(uri: Uri?) {
        // A null URI means that the user left the file picker without picking a directory
        if (uri == null) {
            return
        }

        val path = parseDocTreePath(uri)

        if (path != null) {
            blacklistModel.addPath(path)
        } else {
            // TODO: Tolerate this once the excluded system is modernized
            requireContext().showToast(R.string.err_bad_dir)
        }
    }

    private fun parseDocTreePath(uri: Uri): String? {
        // Turn the raw URI into a document tree URI
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            uri, DocumentsContract.getTreeDocumentId(uri)
        )

        // Turn it into a semi-usable path
        val typeAndPath = DocumentsContract.getTreeDocumentId(docUri).split(":")

        // Only the main drive is supported, since thats all we can get from MediaColumns.DATA
        // Unless I change the system to use the drive/directory system, but thats limited to
        // Android 10
        if (typeAndPath[0] == "primary") {
            return getRootPath() + "/" + typeAndPath.last()
        }

        return null
    }

    private fun saveAndRestart() {
        blacklistModel.save {
            playbackModel.savePlaybackState(requireContext(), ::hardRestart)
        }
    }

    private fun hardRestart() {
        logD("Performing hard restart.")

        // Instead of having to do a ton of cleanup and horrible code changes
        // to restart this application non-destructively, I just restart the UI task [There is only
        // one, after all] and then kill the application using exitProcess. Works well enough.
        val intent = Intent(requireContext().applicationContext, MainActivity::class.java).setFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        )

        startActivity(intent)

        exitProcess(0)
    }

    /**
     * Get *just* the root path, nothing else is really needed.
     */
    @Suppress("DEPRECATION")
    private fun getRootPath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    companion object {
        const val TAG = "TAG_BLACKLIST_DIALOG"
    }
}
