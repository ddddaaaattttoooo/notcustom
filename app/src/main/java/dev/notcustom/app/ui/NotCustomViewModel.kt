package dev.notcustom.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.notcustom.app.core.AnimScales
import dev.notcustom.app.core.Density
import dev.notcustom.app.core.PhoneSettings
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
    val canSecure: Boolean = false,
    val fontWeight: Int = 0,
    val canSystem: Boolean = false,
    val phone: PhoneSettings = PhoneSettings(),
    val canDensity: Boolean = false,
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
            val ready = shizuku == ShizukuState.READY
            // One grant through Shizuku lets animations work later without it, even after reboot.
            if (ready && !Tweaks.canWriteSecure(app)) runCatching { Tweaks.grantSecureWithShizuku(app) }
            _state.update {
                it.copy(
                    shizuku = shizuku,
                    loading = false,
                    hasSnapshot = Presets.exists(app),
                    canSecure = ready || Tweaks.canWriteSecure(app),
                    canSystem = ready || Tweaks.canWriteSystem(app),
                    canDensity = ready
                )
            }
            runCatching {
                val anim = Tweaks.animScales(app)
                val font = Tweaks.fontScale(app)
                val density = Tweaks.density(app, ready)
                val phone = Tweaks.phone(app)
                val weight = Tweaks.fontWeight(app)
                _state.update {
                    it.copy(anim = anim, fontScale = font, density = density, phone = phone, fontWeight = weight)
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
            runCatching { Tweaks.setAnimScales(getApplication(), anim) }
                .onSuccess { _state.update { it.copy(anim = anim, message = null) } }
                .onFailure { t -> fail(t) }
        }
    }

    fun setPhone(phone: PhoneSettings) {
        viewModelScope.launch {
            runCatching { Tweaks.setPhone(getApplication(), phone) }
                .onSuccess { _state.update { it.copy(phone = phone, message = null) } }
                .onFailure { t -> fail(t) }
        }
    }

    fun setFontWeight(value: Int) {
        viewModelScope.launch {
            runCatching { Tweaks.setFontWeight(getApplication(), value) }
                .onSuccess { _state.update { it.copy(fontWeight = value, message = null) } }
                .onFailure { t -> fail(t) }
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch {
            runCatching { Tweaks.setFontScale(getApplication(), value) }
                .onSuccess { _state.update { it.copy(fontScale = value, message = null) } }
                .onFailure { t -> fail(t) }
        }
    }

    fun setDensity(dpi: Int?) {
        viewModelScope.launch {
            runCatching {
                if (dpi == null) Tweaks.resetDensity() else Tweaks.setDensity(dpi)
                Tweaks.density(getApplication(), true)
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
                Tweaks.apply(getApplication(), snapshot, _state.value.canDensity)
            }.onSuccess {
                _state.update { it.copy(message = "Preset applied") }
                refresh()
            }.onFailure { t -> fail(t) }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            runCatching { Tweaks.resetAll(getApplication(), _state.value.canDensity) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            anim = AnimScales(),
                            fontScale = 1f,
                            fontWeight = 0,
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
