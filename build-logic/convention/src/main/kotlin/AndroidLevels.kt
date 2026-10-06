// Android 11 is the floor because favourites and the trash live in MediaStore columns (IS_FAVORITE, IS_TRASHED) that only exist from API 30.
object AndroidLevels {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 30
    const val TARGET_SDK = 36
}
