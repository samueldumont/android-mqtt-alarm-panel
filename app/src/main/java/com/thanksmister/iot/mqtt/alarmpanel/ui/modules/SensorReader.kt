/*
 * Copyright (c) 2018 ThanksMister LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thanksmister.iot.mqtt.alarmpanel.ui.modules

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class SensorReader @Inject
constructor(private val context: Context){

    private val mSensorManager: SensorManager?
    private val mSensorList = ArrayList<Sensor>()
    private val batteryHandler = Handler()
    private var updateFrequencyMilliSeconds: Int = 0
    private var callback: SensorCallback? = null
    private var sensorsPublished: Boolean = false
    private var lightSensorEvent: SensorEvent? = null

    private val batteryHandlerRunnable = object : Runnable {
        override fun run() {
            if (updateFrequencyMilliSeconds > 0) {
                Timber.d("Updating Battery")
                getBatteryReading()
                batteryHandler.postDelayed(this, updateFrequencyMilliSeconds.toLong())
                sensorsPublished = false
            }
        }
    }

    init {
        Timber.d("Creating SensorReader")
        mSensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
        for (s in mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (getSensorName(s.type) != null)
                mSensorList.add(s)
        }
    }

    fun startReadings(freqSeconds: Int, callback: SensorCallback) {
        Timber.d("startReadings")
        this.callback = callback
        if (freqSeconds >= 0) {
            updateFrequencyMilliSeconds = 1000 * freqSeconds
            batteryHandler.postDelayed(batteryHandlerRunnable, updateFrequencyMilliSeconds.toLong())
            startSensorReadings()
        }
    }

    fun stopReadings() {
        Timber.d("stopReadings")
        batteryHandler.removeCallbacksAndMessages(batteryHandlerRunnable)
        updateFrequencyMilliSeconds = 0
        stopSensorReading()
    }

    private fun publishSensorData(sensorName: String?, sensorData: JSONObject) {
        Timber.d("publishSensorData $sensorName")
        if(sensorName != null) {
            callback?.publishSensorData(sensorName, sensorData)
        }
    }

    private fun getSensorName(sensorType: Int): String? {
        when (sensorType) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> return TEMPERATURE
            Sensor.TYPE_LIGHT -> return LIGHT
            Sensor.TYPE_MAGNETIC_FIELD -> return MAGNETIC_FIELD
            Sensor.TYPE_PRESSURE -> return PRESSURE
            Sensor.TYPE_RELATIVE_HUMIDITY -> return HUMIDITY
        }
        return null
    }

    private fun getSensorUnit(sensorType: Int): String? {
        when (sensorType) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> return UNIT_C
            Sensor.TYPE_LIGHT -> return UNIT_LX
            Sensor.TYPE_MAGNETIC_FIELD -> return UNIT_UT
            Sensor.TYPE_PRESSURE -> return UNIT_HPA
            Sensor.TYPE_RELATIVE_HUMIDITY -> return UNIT_PERCENTAGE
        }
        return null
    }

    /**
     * Start all sensor readings.
     */
    private fun startSensorReadings() {
        Timber.d("startSensorReadings $mSensorList")
        if(mSensorManager != null) {
            for (sensor in mSensorList) {
                mSensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    /**
     * Stop all sensor readings.
     */
    private fun stopSensorReading() {
        Timber.d("stopSensorReading")
        for (sensor in mSensorList) {
            mSensorManager?.unregisterListener(sensorListener, sensor)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if(event != null && !sensorsPublished) {
                var data = JSONObject()
                if(event.sensor.type == Sensor.TYPE_LIGHT) {
                    lightSensorEvent = event
                }
                if(lightSensorEvent != null) {
                    data.put(VALUE, lightSensorEvent!!.values[0])
                    data.put(UNIT, getSensorUnit(lightSensorEvent!!.sensor.type))
                    data.put(ID, lightSensorEvent!!.sensor.name)
                    publishSensorData(getSensorName(lightSensorEvent!!.sensor.type), data)
                }
                data = JSONObject()
                data.put(VALUE, event.values[0])
                data.put(UNIT, getSensorUnit(event.sensor.type))
                data.put(ID, event.sensor.name)
                publishSensorData(getSensorName(event.sensor.type), data)
                sensorsPublished = true
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    // TODO let's move this to its own setting
    private fun getBatteryReading() {
        Timber.d("getBatteryReading")
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val batteryStatusIntExtra = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = batteryStatusIntExtra == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatusIntExtra == BatteryManager.BATTERY_STATUS_FULL
        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val data = JSONObject()
        try {
            data.put(VALUE, level)
            data.put(UNIT, UNIT_PERCENTAGE)
            data.put(CHARGING, isCharging)
            data.put(AC_PLUGGED, acCharge)
            data.put(USB_PLUGGED, usbCharge)
        } catch (ex: JSONException) {
            ex.printStackTrace()
        }

        publishSensorData(BATTERY, data)
    }

    companion object {
        const val BATTERY: String = "battery"
        const val CHARGING: String = "charging"
        const val AC_PLUGGED: String = "acPlugged"
        const val USB_PLUGGED: String = "usbPlugged"
        const val HUMIDITY: String = "humidity"
        const val LIGHT: String = "light"
        const val PRESSURE: String = "pressure"
        const val TEMPERATURE: String = "temperature"
        const val MAGNETIC_FIELD: String = "magneticField"
        const val UNIT_C: String = "°C"
        const val UNIT_PERCENTAGE: String = "%"
        const val UNIT_HPA: String = "hPa"
        const val UNIT_UT: String = "uT"
        const val UNIT_LX: String = "lx"
        const val VALUE = "value"
        const val UNIT = "unit"
        const val ID = "id"
    }
}