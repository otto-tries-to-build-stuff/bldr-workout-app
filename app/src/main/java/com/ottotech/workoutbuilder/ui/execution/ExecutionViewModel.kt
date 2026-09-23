package com.ottotech.workoutbuilder.ui.execution

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.execution.ExecutionService
import com.ottotech.workoutbuilder.execution.ExecutionState
import kotlinx.coroutines.flow.StateFlow

/**
 * A view of the service's state, plus the commands the screen can send.
 *
 * It holds no clock and no session of its own: when the screen is destroyed and rebuilt
 * (rotation, or returning from the notification) it re-reads whatever the service has, which
 * is the only copy that matters.
 */
class ExecutionViewModel(app: Application) : AndroidViewModel(app) {

    /** Straight through: the service's flow is process-wide and already the single source. */
    val state: StateFlow<ExecutionState> = ExecutionService.state

    private val context get() = getApplication<Application>()

    fun start(workoutId: String) = ExecutionService.start(context, workoutId)
    fun pause() = ExecutionService.pause(context)
    fun resume() = ExecutionService.resume(context)
    fun skip() = ExecutionService.skip(context)
    fun complete() = ExecutionService.complete(context)
    fun toggleMute() = ExecutionService.toggleMute(context)
    fun stop() = ExecutionService.stop(context)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ExecutionViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                )
            }
        }
    }
}
