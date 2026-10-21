package app.signsetu.camera

import android.content.Context

/** Release builds: the bundled holistic model. */
object LandmarkTrackers {
    fun create(context: Context): LandmarkTracker? = holisticTracker(context)
}
