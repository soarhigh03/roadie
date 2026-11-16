package ai.roadie.app.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DrivingAccess(val known: Boolean = false, val settingsAllowed: Boolean = false, val detailsAllowed: Boolean = false)

/** Unknown or disconnected vehicle state never unlocks setup. */
class DrivingRestrictions(context: Context) {
    private val mutable = MutableStateFlow(DrivingAccess())
    val access = mutable.asStateFlow()
    private var manager: CarUxRestrictionsManager? = null
    private var car: Car? = null
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { update(it) }
    init {
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (ready) try {
                    manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    manager?.registerListener(listener)
                    manager?.currentCarUxRestrictions?.let(::update)
                } catch (_: Exception) { mutable.value = DrivingAccess() }
                else { manager = null; mutable.value = DrivingAccess() }
            }
        } catch (_: Exception) { mutable.value = DrivingAccess() }
        catch (_: LinkageError) { mutable.value = DrivingAccess() }
    }
    private fun update(r: CarUxRestrictions) {
        val active = r.activeRestrictions
        val parked = !r.isRequiresDistractionOptimization
        mutable.value = DrivingAccess(true,
            parked && active and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP == 0,
            parked && active and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT == 0)
    }
    fun close() { runCatching { manager?.unregisterListener() }; car?.disconnect(); mutable.value = DrivingAccess() }
}
