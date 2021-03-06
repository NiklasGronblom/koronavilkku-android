package fi.thl.koronahaavi.settings

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.common.api.Status
import fi.thl.koronahaavi.common.Event
import fi.thl.koronahaavi.device.DeviceStateRepository
import fi.thl.koronahaavi.service.ExposureNotificationService
import fi.thl.koronahaavi.service.ExposureNotificationService.ResolvableResult

class EnableSystemViewModel @ViewModelInject constructor(
    private val exposureNotificationService: ExposureNotificationService,
    deviceStateRepository: DeviceStateRepository
) : ViewModel() {

    private val enableResolutionRequiredEvent = MutableLiveData<Event<Status>>()
    fun enableResolutionRequired(): LiveData<Event<Status>> = enableResolutionRequiredEvent

    private val enableENErrorEvent = MutableLiveData<Event<EnableENError>>()
    fun enableErrorEvent(): LiveData<Event<EnableENError>> = enableENErrorEvent

    private val isBluetoothOn = deviceStateRepository.bluetoothOn()
    private val isLocationOn = deviceStateRepository.locationOn()
    private val requiredServices = MediatorLiveData<Boolean>().apply {
        addSource(isBluetoothOn) { updateRequiredServices() }
        addSource(isLocationOn) { updateRequiredServices() }
    }

    private fun updateRequiredServices() {
        requiredServices.value = isBluetoothOn.value==true && isLocationOn.value==true
    }

    suspend fun enableSystem(): Boolean {
        // we cannot directly enable bluetooth/location, but disabling/enabling EN will also enable
        // bluetooth/location.. this has no effect if EN was already disabled
        if (requiredServices.value != true) {
            exposureNotificationService.disable()
        }

        return when (val result = exposureNotificationService.enable()) {
            is ResolvableResult.Success -> true
            is ResolvableResult.ResolutionRequired -> {
                enableResolutionRequiredEvent.postValue(Event(result.status))
                false
            }
            is ResolvableResult.Failed -> {
                enableENErrorEvent.postValue(Event(
                    EnableENError.Failed(code = result.apiErrorCode)
                ))
                false
            }
            is ResolvableResult.MissingCapability -> {
                enableENErrorEvent.postValue(Event(
                    EnableENError.MissingCapability
                ))
                false
            }
        }
    }

    suspend fun disableSystem() {
        exposureNotificationService.disable()
    }
}

sealed class EnableENError {
    object MissingCapability : EnableENError()
    data class Failed(val code: Int? = null, val error: String? = null): EnableENError()
}