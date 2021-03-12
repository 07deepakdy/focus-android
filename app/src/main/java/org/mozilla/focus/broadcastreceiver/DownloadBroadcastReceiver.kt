/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.focus.broadcastreceiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.installreferrer.BuildConfig


import com.google.android.material.snackbar.Snackbar
import mozilla.components.support.utils.DownloadUtils.guessFileName

import org.mozilla.focus.R
import org.mozilla.focus.utils.IntentUtils


import java.io.File
import java.util.HashSet

/**
 * BroadcastReceiver for finished downloads
 */
class DownloadBroadcastReceiver(private val browserContainer: View, private val downloadManager: DownloadManager) : BroadcastReceiver() {
    private val queuedDownloadReferences = HashSet<Long>()
    override fun onReceive(context: Context, intent: Intent) {
        val downloadReference: Long = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        displaySnackbar(context, downloadReference, downloadManager)
    }

    private fun displaySnackbar(context: Context, completedDownloadReference: Long, downloadManager: DownloadManager) {
        if (!isFocusDownload(completedDownloadReference)) {
            return
        }
        val query = DownloadManager.Query()
        query.setFilterById(completedDownloadReference)
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusColumnIndex)) {
                    val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                    val localUri = if (uriString.startsWith(FILE_SCHEME)) uriString.substring(FILE_SCHEME.length) else uriString
                    val decoded = Uri.decode(localUri)
                    var file = File(decoded)
                    val mimeType = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE))
                    var fileName: String = guessFileName(null, null, decoded, null)
                    val fileName2: String = guessFileName(null, null, decoded, mimeType)
                    // Rename the file extension if it lacks a known MIME type and the server provided a Content-Type header.
                    if (fileName != fileName2) {
                        val file2 = File(file.parent, fileName2)
                        if (file.renameTo(file2)) {
                            file = file2
                            fileName = fileName2
                        }
                    }
                    val uriForFile = FileProvider.getUriForFile(context, BuildConfig.DEBUG.toString() + FILE_PROVIDER_EXTENSION, file)
                    val openFileIntent: Intent? = IntentUtils.createOpenFileIntent(uriForFile, mimeType)
                    showSnackbarForFilename(openFileIntent, context, fileName)
                }
            }
        }
        removeFromHashSet(completedDownloadReference)
    }

    private fun showSnackbarForFilename(openFileIntent: Intent?, context: Context, fileName: String) {
        val snackbar: Snackbar = Snackbar
                .make(browserContainer, String.format(context.getString(R.string.download_snackbar_finished), fileName), Snackbar.LENGTH_LONG)
        if (IntentUtils.activitiesFoundForIntent(context, openFileIntent)) {
            snackbar.setAction(context.getString(R.string.download_snackbar_open), View.OnClickListener { context.startActivity(openFileIntent) })
            snackbar.setActionTextColor(ContextCompat.getColor(context, R.color.snackbarActionText))
        }
        snackbar.show()
    }

    private fun isFocusDownload(completedDownloadReference: Long): Boolean {
        return queuedDownloadReferences.contains(completedDownloadReference)
    }

    private fun removeFromHashSet(completedDownloadReference: Long) {
        queuedDownloadReferences.remove(completedDownloadReference)
    }

    fun addQueuedDownload(referenceId: Long) {
        queuedDownloadReferences.add(referenceId)
    }

    companion object {
        private const val FILE_SCHEME = "file://"
        private const val FILE_PROVIDER_EXTENSION = ".fileprovider"
    }
}