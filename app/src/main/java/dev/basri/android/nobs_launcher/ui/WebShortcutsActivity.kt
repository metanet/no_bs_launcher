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
    private lateinit var service: WebShortcutService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        binding = ActivityWebShortcutsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = LayoutStore(this)
        val favicons = FaviconRepository(this)
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

        val requestedEdit = intent.getStringExtra(EXTRA_EDIT_SHORTCUT_ID)
        if (requestedEdit != null && savedInstanceState == null) {
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

    private fun refresh() {
        val shortcuts = store.load().shortcuts
        adapter.submit(shortcuts)
        binding.shortcutEmpty.visibility = if (shortcuts.isEmpty()) View.VISIBLE else View.GONE
        binding.shortcutList.visibility = if (shortcuts.isEmpty()) View.GONE else View.VISIBLE
        if (shortcuts.isEmpty()) {
            binding.addShortcut.post { binding.addShortcut.requestFocus() }
        }
    }

    private fun showEditor(shortcut: WebShortcut?) {
        val editor = DialogWebShortcutBinding.inflate(layoutInflater)
        editor.shortcutName.setText(shortcut?.name.orEmpty())
        editor.shortcutUrl.setText(shortcut?.url.orEmpty())
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (shortcut == null) R.string.add_shortcut else R.string.edit_shortcut)
            .setView(editor.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.show()
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            editor.shortcutName.error = null
            editor.shortcutUrl.error = null
            val result = service.save(
                existingUuid = shortcut?.uuid,
                name = editor.shortcutName.text.toString(),
                url = editor.shortcutUrl.text.toString(),
                onIconUpdated = {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) refresh()
                    }
                },
            )
            when (result) {
                is SaveShortcutResult.Invalid -> showValidationError(editor, result.validation)
                SaveShortcutResult.SaveFailed -> Toast.makeText(
                    this,
                    R.string.shortcut_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                is SaveShortcutResult.Saved -> {
                    refresh()
                    dialog.dismiss()
                }
            }
        }
        editor.shortcutName.requestFocus()
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
    }
}
