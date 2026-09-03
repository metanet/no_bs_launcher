package dev.basri.android.nobs_launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.FaviconRepository
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.data.LauncherServices
import dev.basri.android.nobs_launcher.data.SaveShortcutRequest
import dev.basri.android.nobs_launcher.data.SaveShortcutResult
import dev.basri.android.nobs_launcher.data.WebShortcutService
import dev.basri.android.nobs_launcher.databinding.ActivityWebShortcutsBinding
import dev.basri.android.nobs_launcher.databinding.DialogWebShortcutBinding
import dev.basri.android.nobs_launcher.model.ShortcutError
import dev.basri.android.nobs_launcher.model.ShortcutField
import dev.basri.android.nobs_launcher.model.ShortcutInput
import dev.basri.android.nobs_launcher.model.WebShortcut

class WebShortcutsActivity : Activity() {
    private lateinit var binding: ActivityWebShortcutsBinding
    private lateinit var store: LayoutStore
    private lateinit var adapter: WebShortcutsAdapter
    private lateinit var favicons: FaviconRepository
    private lateinit var service: WebShortcutService
    private var activeSaveRequest: SaveShortcutRequest? = null
    private var activeEditorDialog: AlertDialog? = null
    private var activeEditorBinding: DialogWebShortcutBinding? = null
    private var activeEditorUuid: String? = null
    private var editorSaveInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        binding = ActivityWebShortcutsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = LayoutStore(this)
        favicons = LauncherServices.favicons(this)
        service = WebShortcutService(store, favicons)
        adapter = WebShortcutsAdapter(
            favicons = favicons,
            onEdit = ::showEditor,
            onRemove = ::confirmRemoval,
        )
        binding.shortcutList.layoutManager = LinearLayoutManager(this)
        binding.shortcutList.adapter = adapter
        binding.addShortcut.setOnClickListener { showEditor(null) }
        binding.backToSettings.setOnClickListener { finish() }

        if (savedInstanceState?.getBoolean(STATE_EDITOR_OPEN) == true) {
            binding.root.post {
                val uuid = savedInstanceState.getString(STATE_EDITOR_UUID)
                val shortcut = uuid?.let { requestedUuid ->
                    store.load().shortcuts.firstOrNull { it.uuid == requestedUuid }
                }
                showEditor(
                    shortcut = shortcut,
                    initialName = savedInstanceState.getString(STATE_EDITOR_NAME).orEmpty(),
                    initialUrl = savedInstanceState.getString(STATE_EDITOR_URL).orEmpty(),
                    restartSave = savedInstanceState.getBoolean(STATE_EDITOR_SAVING),
                )
            }
        } else {
            val requestedEdit = intent.getStringExtra(EXTRA_EDIT_SHORTCUT_ID)
            if (requestedEdit == null) return
            binding.root.post {
                store.load().shortcuts
                    .firstOrNull { it.uuid == requestedEdit }
                    ?.let(::showEditor)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        activeSaveRequest?.cancel()
        activeSaveRequest = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val editor = activeEditorBinding
        val dialog = activeEditorDialog
        if (editor != null && dialog?.isShowing == true) {
            outState.putBoolean(STATE_EDITOR_OPEN, true)
            outState.putString(STATE_EDITOR_UUID, activeEditorUuid)
            outState.putString(STATE_EDITOR_NAME, editor.shortcutName.text.toString())
            outState.putString(STATE_EDITOR_URL, editor.shortcutUrl.text.toString())
            outState.putBoolean(STATE_EDITOR_SAVING, editorSaveInProgress)
        }
        super.onSaveInstanceState(outState)
    }

    private fun refresh() {
        val shortcuts = store.load().shortcuts
        adapter.submit(shortcuts)
        binding.shortcutEmpty.visibility = if (shortcuts.isEmpty()) View.VISIBLE else View.GONE
        binding.shortcutList.visibility = if (shortcuts.isEmpty()) View.GONE else View.VISIBLE
        if (shortcuts.isEmpty()) {
            binding.addShortcut.post { binding.addShortcut.requestFocus() }
        }
    }

    private fun showEditor(
        shortcut: WebShortcut?,
        initialName: String = shortcut?.name.orEmpty(),
        initialUrl: String = shortcut?.url.orEmpty(),
        restartSave: Boolean = false,
    ) {
        val editor = DialogWebShortcutBinding.inflate(layoutInflater)
        editor.shortcutName.setText(initialName)
        editor.shortcutUrl.setText(initialUrl)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (shortcut == null) R.string.add_shortcut else R.string.edit_shortcut)
            .setView(editor.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.show()
        activeEditorDialog = dialog
        activeEditorBinding = editor
        activeEditorUuid = shortcut?.uuid
        editorSaveInProgress = false
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        dialog.setOnDismissListener {
            editorSaveInProgress = false
            activeSaveRequest?.cancel()
            activeSaveRequest = null
            if (activeEditorDialog === dialog) {
                activeEditorDialog = null
                activeEditorBinding = null
                activeEditorUuid = null
            }
        }
        positiveButton.setOnClickListener {
            if (editorSaveInProgress) return@setOnClickListener
            editor.shortcutName.error = null
            editor.shortcutUrl.error = null
            editorSaveInProgress = true
            setEditorChecking(editor, positiveButton, checking = true)
            activeSaveRequest = service.save(
                existingUuid = shortcut?.uuid,
                name = editor.shortcutName.text.toString(),
                url = editor.shortcutUrl.text.toString(),
                onIconUpdated = {
                    binding.root.post {
                        if (!isFinishing && !isDestroyed) refresh()
                    }
                },
                onComplete = { result ->
                    editor.root.post {
                        if (
                            !dialog.isShowing ||
                            isFinishing ||
                            isDestroyed ||
                            !editorSaveInProgress
                        ) {
                            return@post
                        }
                        activeSaveRequest = null
                        editorSaveInProgress = false
                        when (result) {
                            is SaveShortcutResult.Invalid -> {
                                setEditorChecking(editor, positiveButton, checking = false)
                                showValidationError(editor, result.validation)
                            }
                            SaveShortcutResult.WebsiteInaccessible -> {
                                setEditorChecking(editor, positiveButton, checking = false)
                                editor.shortcutUrl.error = getString(
                                    R.string.shortcut_website_inaccessible,
                                )
                                editor.shortcutUrl.requestFocus()
                            }
                            SaveShortcutResult.SaveFailed -> {
                                setEditorChecking(editor, positiveButton, checking = false)
                                Toast.makeText(
                                    this,
                                    R.string.shortcut_save_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is SaveShortcutResult.Saved -> {
                                refresh()
                                dialog.dismiss()
                            }
                        }
                    }
                }
            )
        }
        editor.shortcutName.requestFocus()
        if (restartSave) editor.root.post(positiveButton::performClick)
    }

    private fun setEditorChecking(
        editor: DialogWebShortcutBinding,
        positiveButton: View,
        checking: Boolean,
    ) {
        editor.shortcutName.isEnabled = !checking
        editor.shortcutUrl.isEnabled = !checking
        positiveButton.isEnabled = !checking
        editor.shortcutChecking.visibility = if (checking) View.VISIBLE else View.GONE
    }

    private fun showValidationError(
        editor: DialogWebShortcutBinding,
        invalid: ShortcutInput.Invalid,
    ) {
        val message = when (invalid.field to invalid.error) {
            ShortcutField.NAME to ShortcutError.REQUIRED -> R.string.shortcut_name_required
            ShortcutField.NAME to ShortcutError.TOO_LONG -> R.string.shortcut_name_too_long
            ShortcutField.URL to ShortcutError.REQUIRED -> R.string.shortcut_url_required
            ShortcutField.URL to ShortcutError.TOO_LONG -> R.string.shortcut_url_too_long
            else -> R.string.shortcut_url_invalid
        }
        when (invalid.field) {
            ShortcutField.NAME -> editor.shortcutName.error = getString(message)
            ShortcutField.URL -> editor.shortcutUrl.error = getString(message)
        }
    }

    private fun confirmRemoval(shortcut: WebShortcut) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.remove_shortcut)
            .setMessage(getString(R.string.confirm_remove_shortcut, shortcut.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove_shortcut) { _, _ ->
                if (service.remove(shortcut.uuid)) {
                    refresh()
                } else {
                    Toast.makeText(this, R.string.shortcut_remove_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus()
        }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        const val EXTRA_EDIT_SHORTCUT_ID = "edit_shortcut_id"
        private const val STATE_EDITOR_OPEN = "shortcut_editor_open"
        private const val STATE_EDITOR_UUID = "shortcut_editor_uuid"
        private const val STATE_EDITOR_NAME = "shortcut_editor_name"
        private const val STATE_EDITOR_URL = "shortcut_editor_url"
        private const val STATE_EDITOR_SAVING = "shortcut_editor_saving"
    }
}
