package com.yg.liquidink.ui

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yg.liquidink.core.PerformanceMode
import com.yg.liquidink.ui.components.GlassSurface
import com.yg.liquidink.ui.editor.EditorScreen
import com.yg.liquidink.ui.home.HomeScreen
import com.yg.liquidink.ui.settings.SettingsScreen
import com.yg.liquidink.ui.theme.LiquidInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OPEN_MIME_TYPES = arrayOf(
    "text/markdown",
    "text/x-markdown",
    "text/plain",
    "application/markdown",
    "application/x-markdown",
    "application/octet-stream",
)

@Composable
fun LiquidInkApp(viewModel: MainViewModel) {
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val surface = resolveAppSurface(
        settingsOpen = settingsOpen,
        hasEditor = editor != null,
    )
    val saveableStateHolder = rememberSaveableStateHolder()

    val context = LocalContext.current
    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }
    val autoReducedEffects = remember(activityManager) {
        activityManager?.isLowRamDevice == true || Build.VERSION.SDK_INT < 29
    }
    val reducedEffects = settings.performanceMode == PerformanceMode.REDUCED ||
        (settings.performanceMode == PerformanceMode.AUTO && autoReducedEffects)
    val systemAnimationsDisabled = rememberSystemAnimationsDisabled()
    val motionEnabled = settings.animationsEnabled &&
        !systemAnimationsDisabled &&
        !reducedEffects

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarIsError by remember { mutableStateOf(false) }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::openDocument)
    }
    val newDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        uri?.let(viewModel::createDocument)
    }
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        uri?.let(viewModel::saveAs)
    }

    val launchNewDocument: () -> Unit = {
        newDocumentLauncher.launch(newDocumentName())
    }
    val launchOpenDocument: () -> Unit = {
        openLauncher.launch(OPEN_MIME_TYPES)
    }
    val launchSaveAs: () -> Unit = {
        saveAsLauncher.launch(editor?.document?.displayName ?: "未命名.md")
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarIsError = message.isError
            snackbarHostState.showSnackbar(message.text)
        }
    }

    LiquidInkTheme(
        themeMode = settings.themeMode,
        highContrast = settings.highContrast,
        reducedEffects = reducedEffects,
        glassEffectsEnabled = settings.glassEffectsEnabled,
        glassIntensity = settings.glassIntensity,
        animationsEnabled = motionEnabled,
    ) {
        Box(Modifier.fillMaxSize()) {
            when (surface) {
                AppSurface.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        onThemeModeChange = viewModel::setThemeMode,
                        onAutoSaveEnabledChange = viewModel::setAutoSaveEnabled,
                        onAutoSaveIntervalChange = viewModel::setAutoSaveIntervalSeconds,
                        onPerformanceModeChange = viewModel::setPerformanceMode,
                        onHighContrastChange = viewModel::setHighContrast,
                        onReaderBackgroundChange = viewModel::setReaderBackground,
                        onGlassEffectsEnabledChange = viewModel::setGlassEffectsEnabled,
                        onGlassIntensityChange = viewModel::setGlassIntensity,
                        onAnimationsEnabledChange = viewModel::setAnimationsEnabled,
                        onDismiss = { settingsOpen = false },
                    )
                }

                else -> {
                    saveableStateHolder.SaveableStateProvider("main") {
                        AnimatedContent(
                            targetState = editor,
                            contentKey = { it != null },
                            transitionSpec = {
                                if (motionEnabled) {
                                    (fadeIn(tween(240)) + scaleIn(
                                        initialScale = 0.985f,
                                        animationSpec = tween(260),
                                    )).togetherWith(
                                        fadeOut(tween(160)) + scaleOut(
                                            targetScale = 0.992f,
                                            animationSpec = tween(180),
                                        ),
                                    )
                                } else {
                                    fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                                }
                            },
                            label = "home-editor",
                        ) { currentEditor ->
                            if (currentEditor != null) {
                                EditorScreen(
                                    state = currentEditor,
                                    readerBackground = settings.readerBackground,
                                    onValueChange = viewModel::onEditorValueChange,
                                    onUndo = viewModel::undo,
                                    onRedo = viewModel::redo,
                                    onFormat = viewModel::applyFormat,
                                    onSave = { viewModel.save() },
                                    onSaveAs = launchSaveAs,
                                    onRename = viewModel::renameDocument,
                                    onDelete = viewModel::deleteDocument,
                                    onOpenSettings = { settingsOpen = true },
                                    onToggleTask = viewModel::toggleTask,
                                    onSetSelection = viewModel::setSelection,
                                    onReplaceRange = viewModel::replaceRange,
                                    onReplaceAll = viewModel::replaceAll,
                                    onSaveAndClose = viewModel::saveAndClose,
                                    onDiscardAndClose = viewModel::closeEditor,
                                )
                            } else {
                                HomeScreen(
                                    recent = recent,
                                    settings = settings,
                                    onNewDocument = launchNewDocument,
                                    onOpenDocument = launchOpenDocument,
                                    onOpenRecent = { document ->
                                        viewModel.openDocument(Uri.parse(document.uri))
                                    },
                                    onRenameRecent = { document, newName ->
                                        viewModel.renameRecent(document.uri, newName)
                                    },
                                    onDeleteRecent = { document ->
                                        viewModel.deleteRecent(document.uri)
                                    },
                                    onForgetRecent = { document ->
                                        viewModel.forgetRecent(document.uri)
                                    },
                                    onOpenSettings = { settingsOpen = true },
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp)
                    .padding(
                        bottom = when (surface) {
                            AppSurface.EDITOR -> 138.dp
                            else -> 12.dp
                        },
                    ),
            ) { data ->
                GlassSurface(
                    cornerRadius = 18.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    tint = if (snackbarIsError) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    },
                ) {
                    Row(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (snackbarIsError) {
                                Icons.Rounded.ErrorOutline
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                            tint = if (snackbarIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

        }
    }
}

private fun newDocumentName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
    return "未命名-$stamp.md"
}

@Composable
private fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    fun readSystemScaleDisabled(): Boolean = runCatching {
        AndroidSettings.Global.getFloat(
            context.contentResolver,
            AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

    var disabled by remember { mutableStateOf(readSystemScaleDisabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                disabled = readSystemScaleDisabled()
            }
        }
        context.contentResolver.registerContentObserver(
            AndroidSettings.Global.getUriFor(AndroidSettings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return disabled
}
