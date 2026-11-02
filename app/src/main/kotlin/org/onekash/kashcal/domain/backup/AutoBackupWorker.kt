package org.onekash.kashcal.domain.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.preferences.PreferencesKeys
import java.time.Instant
import java.time.ZoneId

/**
 * Periodically exports the settings backup JSON to a user-selected SAF DocumentTree folder (FORK:auto-backup).
 * Uses native AOSP DocumentsContract API without external dependencies.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val exporter: SettingsBackupExporter,
    private val dataStore: KashCalDataStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = dataStore.dataStore.data.first()
        val isEnabled = prefs[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false
        val treeUriStr = prefs[PreferencesKeys.AUTO_BACKUP_TREE_URI]

        if (!isEnabled || treeUriStr.isNullOrBlank()) {
            return Result.success()
        }

        val treeUri = Uri.parse(treeUriStr)

        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            val json = exporter.exportSettings(includeForkPreferences = true)
            val filename = BackupFilename.generate(Instant.now(), ZoneId.systemDefault())
            val autoFilename = filename.replace("kashcal-backup-", "kashcal-auto-backup-")

            val fileUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                BACKUP_MIME_TYPE,
                autoFilename
            )

            if (fileUri == null) {
                Log.e(TAG, "Failed to create document in $treeUri with name $autoFilename")
                return Result.retry()
            }

            val outputStream = context.contentResolver.openOutputStream(fileUri)
                ?: throw IllegalStateException("Cannot open output stream for created document $fileUri")
            outputStream.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }

            // Record execution timestamp
            dataStore.edit { it[PreferencesKeys.AUTO_BACKUP_LAST_RUN_MS] = System.currentTimeMillis() }

            // Prune excess backups according to retention limit
            val retentionLimit = prefs[PreferencesKeys.AUTO_BACKUP_RETENTION_COUNT] ?: DEFAULT_RETENTION_COUNT
            pruneOldBackups(treeUri, treeDocId, retentionLimit)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed with exception", e)
            Result.retry()
        }
    }

    private fun pruneOldBackups(treeUri: Uri, treeDocId: String, limit: Int) {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            data class BackupFileInfo(val docId: String, val name: String, val lastModified: Long)
            val backups = mutableListOf<BackupFileInfo>()

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: ""
                    if (name.startsWith("kashcal-auto-backup-")) {
                        val lastMod = if (cursor.isNull(modCol)) 0L else cursor.getLong(modCol)
                        backups.add(BackupFileInfo(cursor.getString(idCol), name, lastMod))
                    }
                }
            }

            backups.sortWith(compareByDescending<BackupFileInfo> { it.lastModified }.thenByDescending { it.name })
            if (backups.size > limit) {
                for (old in backups.drop(limit)) {
                    val oldDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, old.docId)
                    DocumentsContract.deleteDocument(context.contentResolver, oldDocUri)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune old auto-backup files", e)
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "kashcal_auto_backup"
        const val DEFAULT_RETENTION_COUNT = 5
        private const val BACKUP_MIME_TYPE = "application/json"
    }
}
