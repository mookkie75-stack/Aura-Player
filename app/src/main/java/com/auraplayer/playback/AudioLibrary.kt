package com.auraplayer.playback

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.auraplayer.Track

object AudioLibrary {
    fun query(context: Context): List<Track> {
        val resolver: ContentResolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        val tracks = mutableListOf<Track>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                tracks += Track(
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleColumn) ?: "Unknown title",
                    artist = cursor.getString(artistColumn) ?: "Unknown artist",
                    album = cursor.getString(albumColumn) ?: ""
                )
            }
        }
        return tracks
    }
}

/** Writes common ID3/Vorbis fields and optional artwork through a user-granted document URI. */
object AudioTagWriter {
    fun update(
        context: Context,
        uri: Uri,
        title: String,
        artist: String,
        album: String,
        artworkUri: Uri?
    ) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Cannot read selected audio document")
        val tempAudio = java.io.File(context.cacheDir, "aura-${System.nanoTime()}.audio")
        input.use { source -> tempAudio.outputStream().use { source.copyTo(it) } }
        try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempAudio)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
            if (artworkUri != null) {
                val tempArt = java.io.File(context.cacheDir, "aura-art-${System.nanoTime()}")
                context.contentResolver.openInputStream(artworkUri)?.use { source ->
                    tempArt.outputStream().use { source.copyTo(it) }
                } ?: error("Cannot read selected image")
                try {
                    tag.deleteArtworkField()
                    tag.setField(org.jaudiotagger.tag.images.ArtworkFactory.createArtworkFromFile(tempArt))
                } finally {
                    tempArt.delete()
                }
            }
            org.jaudiotagger.audio.AudioFileIO.write(audioFile)
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Document provider does not allow writing")
            output.use { destination -> tempAudio.inputStream().use { it.copyTo(destination) } }
        } finally {
            tempAudio.delete()
        }
    }
}
