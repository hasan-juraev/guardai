package com.guardai.tracking

import com.guardai.session.CaptureSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CurrentAppTracker {
    private val mutablePackage = MutableStateFlow<String?>(null)
    val currentPackage = mutablePackage.asStateFlow()

    fun update(packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            mutablePackage.value = packageName
            CaptureSessionState.updateCurrentPackage(packageName)
        }
    }
}
