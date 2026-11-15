package dev.notcustom.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.notcustom.app.core.AnimScales
import dev.notcustom.app.core.Density
import dev.notcustom.app.core.Presets
import dev.notcustom.app.core.Shell
import dev.notcustom.app.core.ShizukuManager
import dev.notcustom.app.core.Snapshot
import dev.notcustom.app.core.Tweaks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, PERMISSION_DENIED, READY }

data class UiState(
    val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
    val loading: Boolean = true,
    val anim: AnimScales = AnimScales(),
    val fontScale: Float = 1f,
    val density: Density? = null,
    val hasSnapshot: Boolean = false,
    val message: String? = null
)

class NotCustomViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        Shell.reset()
        refresh()
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        ShizukuManager.addBinderReceivedListener(binderReceived)
        ShizukuManager.addBinderDeadListener(binderDead)
        ShizukuManager.addPermissionListener(permissionResult)
        refresh()
    }

    override fun onCleared() {
        ShizukuManager.removeBinderReceivedListener(binderReceived)
        ShizukuManager.removeBinderDeadListener(binderDead)
        ShizukuManager.removePermissionListener(permissionResult)
        super.onCleared()
    }

    fun refresh() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val shizuku = when {
                !ShizukuManager.isInstalled(app) -> ShizukuState.NOT_INSTALLED
                !ShizukuManager.isRunning() -> ShizukuState.NOT_RUNNING
                !ShizukuManager.hasPermission() -> ShizukuState.PERMISSION_DENIED
                else -> ShizukuState.READY
            }
            _state.update {
                it.copy(
                    shizuku = shizuku,
                    loading = false,
                    hasSnapshot = Presets.exists(app)
                )
            }
            if (shizuku != ShizukuState.READY) return@launch
            runCatching {
                val anim = Tweaks.animScales()
                val font = Tweaks.fontScale()
                val density = Tweaks.density()
                _state.update {
                    it.copy(anim = anim, fontScale = font, density = density)
                }
            }.onFailure { t ->
                _state.update { it.copy(message = t.message) }
            }
        }
    }

    fun requestPermission() {
        ShizukuManager.requestPermission()
    }

    fun setAnim(anim: AnimScales) {
        viewModelScope.launch {
            runCatching { Tweaks.setAnimScales(anim) }
                .onSuccess { _state.update { it.copy(anim = anim, message = null) } }
                .onFailure { t -> fail(t) }
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch {
            runCatching { Tweaks.setFontScale(value) }
                .onSuccess { _state.update { it.copy(fontScale = value, message = null) } }
                .onFailure { t -> fail(t) }
        }
    }

    fun setDensity(dpi: Int?) {
        viewModelScope.launch {
            runCatching {
                if (dpi == null) Tweaks.resetDensity() else Tweaks.setDensity(dpi)
                Tweaks.density()
            }.onSuccess { density ->
                _state.update { it.copy(density = density, message = null) }
            }.onFailure { t -> fail(t) }
        }
    }

    fun saveSnapshot() {
        val current = _state.value
        Presets.save(
            getApplication(),
            Snapshot(
                window = current.anim.window,
                transition = current.anim.transition,
                animator = current.anim.animator,
                fontScale = current.fontScale,
                density = current.density?.overrideDpi
            )
        )
        _state.update { it.copy(hasSnapshot = true, message = "Preset saved") }
    }

    fun applySnapshot() {
        val snapshot = Presets.load(getApplication()) ?: return
        viewModelScope.launch {
            runCatching {
                Tweaks.setAnimScales(
                    AnimScales(snapshot.window, snapshot.transition, snapshot.animator)
                )
                Tweaks.setFontScale(snapshot.fontScale)
                if (snapshot.density == null) {
                    Tweaks.resetDensity()
                } else {
                    Tweaks.setDensity(snapshot.density)
                }
            }.onSuccess {
                _state.update { it.copy(message = "Preset applied") }
                refresh()
            }.onFailure { t -> fail(t) }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            runCatching { Tweaks.resetAll() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            anim = AnimScales(),
                            fontScale = 1f,
                            message = "Back to stock"
                        )
                    }
                    refresh()
                }
                .onFailure { t -> fail(t) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun fail(t: Throwable) {
        _state.update { it.copy(message = t.message ?: "Something went wrong") }
    }
}
