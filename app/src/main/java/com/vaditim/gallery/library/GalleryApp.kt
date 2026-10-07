package com.vaditim.gallery.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaditim.gallery.components.CloseIcon
import com.vaditim.gallery.components.LocalAccentTarget
import com.vaditim.gallery.components.LocalFavoritesOnly
import com.vaditim.gallery.components.LocalScreenCovered
import com.vaditim.gallery.components.LocalSettingsView
import com.vaditim.gallery.components.LocalTimelineAbove
import com.vaditim.gallery.components.LocalViewerGrowth
import com.vaditim.gallery.components.OverlaySheet
import com.vaditim.gallery.components.Section
import com.vaditim.gallery.components.ShareIcon
import com.vaditim.gallery.components.SheetRow
import com.vaditim.gallery.components.TimelineAbove
import com.vaditim.gallery.components.TimelineAboveHost
import com.vaditim.gallery.components.UndoPill
import com.vaditim.gallery.components.revealItem
import com.vaditim.gallery.components.VisibleMonth
import com.vaditim.gallery.components.rememberVisibleMonth
import com.vaditim.gallery.diagnostics.CrashLog
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.sheet.SettingsSheet
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Type
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val BAR_ROOM = 84.dp
private val PILL_ROOM = 40.dp
private val HEADER_ROOM = 56.dp
private val SETTINGS_BLUR = 6.dp

@Composable
fun GalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(Palette.ground)) {
        if (!access.hasFileAccess) {
            AccessScreen(access)
        } else {
            Library(viewModel)
        }
    }
}

// The library: the section on screen under the floating rows, with the sheets, the picker, review and the viewer over it. State lives in the controller; this only lays the layers out and keeps the effects that tie them to the system.
@Composable
private fun Library(viewModel: GalleryViewModel) {
    val content = rememberLibraryContent(viewModel)
    val controller = rememberLibraryController(viewModel)
    val navigation = controller.navigation
    val selection = controller.selection
    val sheets = controller.sheets
    val viewer = controller.viewer
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Kept between recompositions and remade only when something it reads changes, so the screens under it can skip when the app recomposes for anything else.
    val screen by remember(content, controller) { derivedStateOf { LibraryScreen(navigation, content, selection, controller.memories) } }

    LibraryEffects(controller, content, screen)

    // The buttons change colour on the cut, once the outgoing view has left, never while it is still on screen.
    val accentTarget = screen.accentTarget
    var accent by remember { mutableStateOf(accentTarget) }
    LaunchedEffect(accentTarget) {
        delay(Motion.SECTION_LEAVE_MS.toLong())
        accent = accentTarget
    }
    // The PRIVATE, LOCATIONS or TRASH pill and the folder label sit over the bar, so the content ends that much higher to stay clear of them.
    val insetPadding = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HEADER_ROOM,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BAR_ROOM +
            (if (screen.placePill != null) PILL_ROOM else 0.dp) + (if (screen.isFolderLabelShown) PILL_ROOM else 0.dp),
    )
    // The screen softens behind the settings, so the sheet reads as the one thing in front; pulling the sheet down clears the blur with the finger.
    var settingsPull by remember { mutableFloatStateOf(0f) }
    // The settings button's place, which the settings sheet opens out of.
    var settingsBounds by remember { mutableStateOf<Rect?>(null) }
    val timelineAbove = remember { TimelineAbove() }
    val isViewerUp = viewer.shown != null
    SideEffect { timelineAbove.isViewerShown = isViewerUp }
    // Read only where the layer draws, so the blur moving each frame never recomposes the app.
    val settingsBlur = animateDpAsState(if (sheets.isOpen(AppSheet.SETTINGS)) SETTINGS_BLUR else 0.dp, tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut), label = "settings-blur")
    val hazeState = rememberHazeState()
    val metrics = remember { BarMetrics() }
    CompositionLocalProvider(LocalAccent provides accent, LocalAccentTarget provides accentTarget, LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().onSizeChanged { metrics.screenWidth = it.width }) {
            // A section change is a cut, not a dissolve: the outgoing section is gone fast and at once, the incoming one lands from just below on the overshoot.
            AnimatedContent(
                targetState = navigation.section to navigation.isPrivateSection,
                transitionSpec = {
                    (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
                        slideInVertically(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.backOut)) { it / 40 })
                        .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                },
                label = "section",
                modifier = Modifier.zIndex(-2f).fillMaxSize().graphicsLayer {
                    val radius = settingsBlur.value.toPx() * (1f - settingsPull)
                    renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
                    clip = true
                }.hazeSource(hazeState),
            ) { (shown, isPrivateShown) ->
                // While this section is the one shown it follows the current view; leaving, it keeps the last one it had.
                var ownView by remember { mutableStateOf(screen.settingsView) }
                if (shown == navigation.section && isPrivateShown == navigation.isPrivateSection) ownView = screen.settingsView
                // Each section keeps its own accent while it leaves, so the outgoing one never takes on the next one's colour.
                CompositionLocalProvider(
                    LocalAccent provides if (isPrivateShown || (shown == Section.ALBUMS && navigation.isPrivateMode)) Palette.privateRed else shown.accent,
                    LocalSettingsView provides ownView,
                    LocalFavoritesOnly provides selection.isFavoritesOnly,
                    LocalScreenCovered provides sheets.isOpen(AppSheet.SETTINGS),
                    LocalTimelineAbove provides timelineAbove,
                    LocalViewerGrowth provides viewer::growth,
                ) {
                    SectionContent(shown, isPrivateShown, controller, content, screen, insetPadding)
                }
            }

            // The grid's timeline over the viewer's photo, under everything below.
            TimelineAboveHost(timelineAbove, Modifier.zIndex(-0.5f))

            // Everything from here up floats over the content and blurs it; none of it is inside the haze source, or it would blur itself.
            // With a photo open the top buttons and the navigation pop away the way they do when a selection begins, and pop back in when it closes.
            val visibleMonth = screen.folderMemory?.let { rememberVisibleMonth(screen.gridItems, it).value } ?: VisibleMonth("", 0)
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            Box {
                TopRow(
                    isHidden = viewer.isOpen,
                    month = visibleMonth,
                    title = screen.folderName.takeUnless { Settings.folderLabel },
                    isMonthFilled = !Settings.folderLabel,
                    selectedCount = screen.selectedCount,
                    onBack = if (screen.canGoBack) { { backDispatcher?.onBackPressed() } } else null,
                    onAdd = screen.addTarget?.let { target -> { navigation.picker = target } },
                    onToggleView = screen.toggleView,
                    isAlbumsView = screen.isAlbumsView,
                    onCancelSelection = controller::clearSelection,
                    onSettings = { sheets.show(AppSheet.SETTINGS) },
                    isSettingsOpen = sheets.isOpen(AppSheet.SETTINGS),
                    onSettingsBounds = { settingsBounds = it },
                )
            }
            FavoritesCorner(controller, screen, visibleMonth, metrics)
            // Over the open photo, since the nav is its bar too.
            BottomControls(controller, content, screen, metrics, Modifier.align(Alignment.BottomCenter).zIndex(if (isViewerUp) 0.5f else 0f).navigationBarsPadding().padding(bottom = 14.dp))

            LibrarySheetHost(controller, content, screen)
            SettingsSheet(
                visible = sheets.isOpen(AppSheet.SETTINGS),
                isCovers = screen.folderMemory == null,
                placeName = screen.folderName,
                origin = settingsBounds,
                // Sorting through the photos on screen; a place of covers has none to go through.
                onReview = screen.reviewSource?.let { source -> { navigation.review = source } },
                onFindDuplicates = { navigation.isFindingDuplicates = true },
                onDismiss = sheets::dismiss,
                onPull = { settingsPull = it },
                onAnnounce = controller.actions::announce,
                onColumnsChanged = { columns -> controller.memories.setColumns(screen.settingsView, screen.gridSource.folderKey, columns) },
            )
            CrashSheet(context)

            PickerOverlay(controller, content)
            ReviewOverlay(controller, content)
            DuplicatesOverlay(controller, content)
            ViewerOverlay(controller, content, screen, metrics, scope)

            // Above the viewer too, since a photo can be deleted or moved from there.
            UndoPill(
                offer = controller.actions.undoOffer,
                onUndo = controller.actions::undo,
                onExpired = controller.actions::expireUndo,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BAR_ROOM),
            )
        }
    }
}

// What ties the library to the system and to time: permissions, orientation, the lock, the secure flag, and letting go of what no longer applies.
@Composable
private fun LibraryEffects(controller: LibraryController, content: LibraryContent, screen: LibraryScreen) {
    val navigation = controller.navigation
    val selection = controller.selection
    val viewer = controller.viewer
    val viewModel = controller.viewModel
    val context = LocalContext.current

    // GPS in photos is stripped by the system unless this is granted; it is asked once, the first time the library shows.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshLocationPermission() }
    LaunchedEffect(Unit) { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }

    // The app stands upright; only a photo or video being viewed turns with the phone.
    val activity = context.findActivity()
    DisposableEffect(viewer.isOpen, activity) {
        activity?.requestedOrientation = if (viewer.isOpen) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }

    SideEffect {
        Settings.view = screen.settingsView
        Settings.folder = screen.gridSource.folderKey
    }

    // An album emptied while open (its last photo unfavourited) closes itself.
    LaunchedEffect(screen.openFavorite == null) { if (screen.openFavorite == null) navigation.openFavoriteAlbum = null }
    LaunchedEffect(screen.openPrivateFavorite == null) { if (screen.openPrivateFavorite == null) navigation.openPrivateFavoriteGroup = null }

    // Changing place lets go of the selection, the favourites-only filter and rearranging.
    LaunchedEffect(navigation.section, navigation.albumsPlace, screen.favoritesView, navigation.isPrivateMode, navigation.openPrivateFavoriteGroup, screen.isPrivateFavoritesGrouped) {
        selection.reset()
        controller.sheets.dismiss()
    }
    BackHandler(enabled = navigation.isPrivateSection, onBack = navigation::backInsidePrivate)
    BackHandler(enabled = selection.isRearranging) { selection.isRearranging = false }
    BackHandler(enabled = screen.isSelecting || screen.isSelectingCovers, onBack = controller::clearSelection)

    // Leaving the app locks Private again, and drops anyone standing in it back to the albums list.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lockPrivate() }
    LaunchedEffect(content.isPrivateUnlocked) {
        if (!content.isPrivateUnlocked) {
            navigation.onPrivateLocked()
            if (viewer.request?.source.isPrivate) viewer.close()
        }
    }
    // Private stays out of screenshots and the recent-apps preview while it is on screen.
    val isShowingPrivate = screen.isShowingPrivate || viewer.request?.source.isPrivate
    DisposableEffect(isShowingPrivate) {
        val window = (context as? Activity)?.window
        if (isShowingPrivate) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(viewer.request) {
        viewer.animate(content::itemsFor) { id -> screen.folderMemory?.revealItem(screen.gridItems, id) }
    }
}

// The previous run's crash, if it had one; shown once so it can be sent on.
@Composable
private fun CrashSheet(context: Context) {
    var lastCrash by remember { mutableStateOf(CrashLog.read(context)) }
    val close = {
        CrashLog.clear(context)
        lastCrash = null
    }
    OverlaySheet(visible = lastCrash != null, label = "LAST CRASH", onDismiss = close) {
        BasicText(
            lastCrash.orEmpty().lines().take(14).joinToString("\n"),
            style = Type.value.copy(fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        SheetRow("Share", icon = { ShareIcon(it) }) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lastCrash.orEmpty()), null))
            close()
        }
        SheetRow("Close", color = Palette.textMuted, icon = { CloseIcon(it) }) { close() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
