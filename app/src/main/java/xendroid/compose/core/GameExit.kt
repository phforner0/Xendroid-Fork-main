package xendroid.compose.core

/**
 * U09: how the game screen leaves, by where the game was started from (the run's launch
 * source). From the library, back to it. From a pinned shortcut or another app (a frontend),
 * back to where the player was: a task the game screen started alone is removed (no empty
 * entry left in Recents), and a task that also holds the library goes behind first, so the
 * player lands in the launcher or that app rather than in a library they did not come from.
 */
object GameExit {
    enum class Way { FINISH, FINISH_AND_REMOVE_TASK, TASK_TO_BACK_THEN_FINISH }

    fun way(launchSource: String, isTaskRoot: Boolean): Way = when {
        launchSource == "library" -> Way.FINISH
        isTaskRoot -> Way.FINISH_AND_REMOVE_TASK
        else -> Way.TASK_TO_BACK_THEN_FINISH
    }
}
