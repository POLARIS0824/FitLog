package com.example.fitlog.editor

import android.util.AtomicFile
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Emulate Android/POSIX replacement on Windows, keeping real AtomicFile stream and recovery logic. */
@Implements(AtomicFile::class)
class AtomicFileRenameShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun rename(source: File, target: File) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
