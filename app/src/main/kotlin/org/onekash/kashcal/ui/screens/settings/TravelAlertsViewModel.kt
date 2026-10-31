package org.onekash.kashcal.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.travel.CircuitBreaker
import org.onekash.kashcal.data.travel.RoutingSource
import org.onekash.kashcal.reminder.travel.TravelLocationProvider
import javax.inject.Inject

/**
 * View model managing departure alerts settings, provider keys, and location permissions.
 */
@HiltViewModel
class TravelAlertsViewModel @Inject constructor(
    private val dataStore: KashCalDataStore,
    private val circuitBreaker: CircuitBreaker,
    private val locationProvider: TravelLocationProvider
) : ViewModel() {

    val tomtomApiKey: StateFlow<String> = dataStore.travelTomTomApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val tomtomEnabled: StateFlow<Boolean> = dataStore.travelTomTomEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val stadiaApiKey: StateFlow<String> = dataStore.travelStadiaApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val stadiaEnabled: StateFlow<Boolean> = dataStore.travelStadiaEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val valhallaEnabled: StateFlow<Boolean> = dataStore.travelValhallaEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val rushHourEnabled: StateFlow<Boolean> = dataStore.travelRushHourEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isValhallaCircuitOpen = MutableStateFlow(circuitBreaker.isOpen(RoutingSource.VALHALLA))
    val isValhallaCircuitOpen: StateFlow<Boolean> = _isValhallaCircuitOpen.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(locationProvider.hasLocationPermission())
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _hasBackgroundPermission = MutableStateFlow(locationProvider.hasBackgroundLocationPermission())
    val hasBackgroundPermission: StateFlow<Boolean> = _hasBackgroundPermission.asStateFlow()

    fun refreshPermissions() {
        _hasLocationPermission.value = locationProvider.hasLocationPermission()
        _hasBackgroundPermission.value = locationProvider.hasBackgroundLocationPermission()
        _isValhallaCircuitOpen.value = circuitBreaker.isOpen(RoutingSource.VALHALLA)
    }

    fun setTomTomApiKey(key: String) {
        viewModelScope.launch {
            dataStore.setTravelTomTomApiKey(key.trim())
            if (key.isNotBlank()) {
                dataStore.setTravelTomTomEnabled(true)
            }
        }
    }

    fun setTomTomEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setTravelTomTomEnabled(enabled)
        }
    }

    fun setStadiaApiKey(key: String) {
        viewModelScope.launch {
            dataStore.setTravelStadiaApiKey(key.trim())
            if (key.isNotBlank()) {
                dataStore.setTravelStadiaEnabled(true)
            }
        }
    }

    fun setStadiaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setTravelStadiaEnabled(enabled)
        }
    }

    fun setValhallaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setTravelValhallaEnabled(enabled)
        }
    }

    fun setRushHourEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setTravelRushHourEnabled(enabled)
        }
    }
}
