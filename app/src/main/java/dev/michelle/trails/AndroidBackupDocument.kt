package dev.michelle.trails

import android.content.ContentResolver
import android.net.Uri
import dev.michelle.trails.domain.BackupLimits
import dev.michelle.trails.presentation.BackupDocument
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Access only the file explicitly chosen in the system document picker. Call on IO. */
class AndroidBackupDocument(private val resolver: ContentResolver, private val uri: Uri) : BackupDocument {
    override fun read(): String {
        val input = checkNotNull(resolver.openInputStream(uri)) { "Could not open the selected file." }
        val bytes = input.use { stream ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= BackupLimits.MAX_BYTES) { "Backup exceeds the 10 MB limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    override fun write(contents: String) {
        val bytes = contents.toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupLimits.MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        val output = checkNotNull(resolver.openOutputStream(uri, "wt")) { "Could not write the selected file." }
        output.use { stream -> stream.write(bytes) }
    }
}
