package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import kotlinx.coroutines.runBlocking
import java.io.File

/** The bundled content in the source tree. */
val ASSETS = File("src/main/assets/content")

/** The content as shipped, read by the app's own loader, manifest checks included. */
val shipped: ContentBundle by lazy { runBlocking { ContentRepository { File(ASSETS, it).inputStream() }.load() } }
