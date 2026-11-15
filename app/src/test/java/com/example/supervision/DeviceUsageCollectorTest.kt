package com.example.supervision

import com.example.data.model.ChildDeviceState
import com.example.data.model.ParentControlRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DeviceUsageCollectorTest {

    @Test
    fun isWithinBedtime_handlesOvernightWindow() {
        val lateNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 15)
        }
        val earlyMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 45)
        }
        val afternoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }

        assertTrue(DeviceUsageCollector.isWithinBedtime("22:00", "06:00", lateNight))
        assertTrue(DeviceUsageCollector.isWithinBedtime("22:00", "06:00", earlyMorning))
        assertFalse(DeviceUsageCollector.isWithinBedtime("22:00", "06:00", afternoon))
    }

    @Test
    fun isWithinBedtime_handlesSameDayWindow() {
        val inside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        val outside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
        }

        assertTrue(DeviceUsageCollector.isWithinBedtime("13:00", "15:00", inside))
        assertFalse(DeviceUsageCollector.isWithinBedtime("13:00", "15:00", outside))
    }

    @Test
    fun firestoreModelDefaults_deserializeSafely() {
        val controls = ParentControlRules()
        val device = ChildDeviceState()
        assertFalse(controls.isPhoneLocked)
        assertEquals("22:00", controls.bedtimeStart)
        assertEquals(0L, device.totalPhoneScreenMinutesToday)
        assertTrue(device.installedApps.isEmpty())
    }
}
