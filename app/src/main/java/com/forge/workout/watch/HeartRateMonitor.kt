package com.forge.workout.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Standard Bluetooth Heart Rate Service — what the GTR 4 exposes with Heart Rate Push on. */
private val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class HrState { Idle, Scanning, Connecting, Connected, Disconnected, NoPermission, NoBluetooth }

data class HrDevice(val name: String, val address: String, val advertisesHeartRate: Boolean)

/**
 * Connects to a BLE heart-rate broadcaster and streams live BPM.
 *
 * Deliberately plain GATT rather than a library: the Heart Rate Service is a fixed standard
 * (0x180D / 0x2A37) and Amazfit's Heart Rate Push presents the watch as an ordinary monitor.
 */
class HeartRateMonitor(private val context: Context) {

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _state = MutableStateFlow(HrState.Idle)
    val state: StateFlow<HrState> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<HrDevice>>(emptyList())
    val found: StateFlow<List<HrDevice>> = _found.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanning = false

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Runtime permissions differ either side of Android 12. */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun bluetoothReady(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) { _state.value = HrState.NoPermission; return }
        if (!bluetoothReady()) { _state.value = HrState.NoBluetooth; return }
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (scanning) return
        _found.value = emptyList()
        scanning = true
        _state.value = HrState.Scanning
        // Unfiltered: some watches only advertise the HR service while a workout is running,
        // so anything with a name is listed and HR advertisers are flagged.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(emptyList<ScanFilter>(), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (_state.value == HrState.Scanning) _state.value = HrState.Idle
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = runCatching { result.device?.name }.getOrNull()
                ?: result.scanRecord?.deviceName ?: return
            val address = result.device?.address ?: return
            val hasHr = result.scanRecord?.serviceUuids?.contains(ParcelUuid(HR_SERVICE)) == true
            val existing = _found.value
            val prior = existing.firstOrNull { it.address == address }
            if (prior != null && (prior.advertisesHeartRate || !hasHr)) return
            _found.value = (existing.filterNot { it.address == address } +
                HrDevice(name, address, hasHr)).sortedByDescending { it.advertisesHeartRate }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = HrState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!hasPermissions()) { _state.value = HrState.NoPermission; return }
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        stopScan()
        disconnect()
        _state.value = HrState.Connecting
        gatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        _bpm.value = null
        if (_state.value != HrState.NoPermission) _state.value = HrState.Idle
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = HrState.Connected
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _bpm.value = null
                    // autoConnect=true keeps trying, so this is "waiting", not "given up".
                    _state.value = HrState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: return
            g.setCharacteristicNotification(characteristic, true)
            val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            parse(characteristic.uuid, value)?.let { _bpm.value = it }
        }

        @Deprecated("Pre-Tiramisu callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            parse(characteristic.uuid, characteristic.value ?: return)?.let { _bpm.value = it }
        }
    }

    /** Heart Rate Measurement: bit 0 of the flags byte picks uint8 vs uint16 BPM. */
    private fun parse(uuid: UUID, value: ByteArray): Int? {
        if (uuid != HR_MEASUREMENT || value.isEmpty()) return null
        val wide = (value[0].toInt() and 0x01) != 0
        val bpm = if (wide) {
            if (value.size < 3) return null
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            if (value.size < 2) return null
            value[1].toInt() and 0xFF
        }
        return bpm.takeIf { it in 1..250 }
    }
}
