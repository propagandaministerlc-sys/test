package app.ultragrid.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native Windows file dialogs (AWT). */
object Dialogs {
    private const val IMAGE_PATTERN = "*.jpg;*.jpeg;*.png;*.webp;*.bmp;*.gif"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun isImage(file: File) = file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS

    fun openImages(owner: Frame, multiple: Boolean): List<File> {
        val dialog = FileDialog(owner, if (multiple) "Add photos" else "Choose a photo", FileDialog.LOAD)
        dialog.isMultipleMode = multiple
        dialog.file = IMAGE_PATTERN
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
        dialog.isVisible = true
        return dialog.files?.toList().orEmpty()
    }

    /** Asks where to save; always returns a .jpg path. */
    fun saveJpeg(owner: Frame, title: String, suggestedName: String): File? {
        val dialog = FileDialog(owner, title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val withExtension = if (name.endsWith(".jpg", true) || name.endsWith(".jpeg", true)) name else "$name.jpg"
        return File(dir, withExtension)
    }
}
