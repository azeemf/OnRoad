package com.onroad.app.ui.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.Toast
import com.diasemi.codelesslib.CodelessEvent
import com.diasemi.codelesslib.CodelessEvent.ScanRestart
import com.diasemi.codelesslib.CodelessScanner
import com.diasemi.codelesslib.CodelessScanner.AdvData
import com.diasemi.codelesslib.CodelessUtil
import com.diasemi.codelesslib.misc.RuntimePermissionChecker
import com.onroad.app.R
import com.onroad.app.databinding.ActivityScanBinding
import com.onroad.app.ui.adapter.scan.ScanAdapter
import com.onroad.app.ui.adapter.scan.ScanItem
import com.onroad.app.ui.base.BaseActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.inject.Inject

class ScanActivity : BaseActivity(), ScanContract.View, OnItemClickListener {

    private val TAG = "ScanActivity"

    private val REQUEST_ENABLE_BLUETOOTH = 1
    private val SCAN_DURATION = 10000
    private val LIST_UPDATE_INTERVAL = 1000

    val PREFERENCES_NAME = "scan-filter-preferences"

    @Inject
    lateinit var presenter: ScanContract.Presenter<ScanContract.View>

    private lateinit var binding: ActivityScanBinding;

    private var scanner: CodelessScanner? = null
    private var scanning = false
    private var pendingConnection: BluetoothDevice? = null
    private var bluetoothDeviceList: java.util.ArrayList<BluetoothDevice>? = null
    private var deviceList: java.util.ArrayList<ScanItem>? = null
    private var filterBluetoothDeviceList: java.util.ArrayList<BluetoothDevice>? = null
    private var filterDeviceList: java.util.ArrayList<ScanItem>? = null
    private var advDataList: java.util.ArrayList<AdvData>? = null
    private var bluetoothScanAdapter: ScanAdapter? = null
    private var deviceListView: ListView? = null
    private var scanFilter: ScanFilter? = null
    private var permissionChecker: RuntimePermissionChecker? = null
    private var handler: Handler? = null
    private var scanTimer: Runnable? = null
    private var lastListUpdate: Long = 0


    private class ScanFilter {
        var name: Pattern? = null
        var address: Pattern? = null
        var advData: Pattern? = null
        var rssi = Int.MIN_VALUE
        var codeless = true
        var dsps = true
        var suota = false
        var other = false
        var unknown = false
        var beacon = false
        var microsoft = false
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanBinding.inflate(layoutInflater);
        setContentView(binding.root);

        activityComponent.inject(this);
        presenter.attachView(this);

        permissionChecker = RuntimePermissionChecker(this, savedInstanceState)
        if (getPreferences(Context.MODE_PRIVATE).getBoolean("oneTimePermissionRationale", true)) {
            getPreferences(Context.MODE_PRIVATE).edit()
                .putBoolean("oneTimePermissionRationale", false).apply()
            permissionChecker!!.setOneTimeRationale(getString(R.string.permission_rationale))
        }

        initView();
    }

    override fun initView() {

        EventBus.getDefault().register(this)
        scanner = CodelessScanner(this)
        handler = Handler()
        bluetoothDeviceList = ArrayList<BluetoothDevice>()
        deviceList = ArrayList<ScanItem>()
        filterBluetoothDeviceList = ArrayList<BluetoothDevice>()
        filterDeviceList = ArrayList<ScanItem>()
        advDataList = ArrayList<AdvData>()
        bluetoothScanAdapter = ScanAdapter(this, R.layout.scan_item_row, filterDeviceList)
        binding.deviceListView.adapter = bluetoothScanAdapter
        binding.deviceListView.setOnItemClickListener(this)
        scanTimer = Runnable { stopDeviceScan() }
        initScanFilter()

        if (BluetoothAdapter.getDefaultAdapter() == null || !packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE
            )
        ) {
            // Device does not support Bluetooth Low Energy
            Log.e(TAG, "Bluetooth Low Energy not supported.")
            Toast.makeText(
                applicationContext,
                "Bluetooth Low Energy is not supported on this device",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
        startDeviceScan()
    }

    private fun initScanFilter() {
        scanFilter = ScanFilter()
        val preferences = getSharedPreferences(PREFERENCES_NAME, 0)
        if (preferences.getBoolean("rssiFilter", false)) {
            try {
                scanFilter!!.rssi = Integer.decode(preferences.getString("rssiLevel", ""))
            } catch (e: NumberFormatException) {
            }
        }
        scanFilter!!.codeless = preferences.getBoolean("codeless", true)
        scanFilter!!.dsps = preferences.getBoolean("dsps", true)
        scanFilter!!.suota = preferences.getBoolean("suota", false)
        scanFilter!!.other = preferences.getBoolean("other", false)
        scanFilter!!.unknown = preferences.getBoolean("unknown", false)
        scanFilter!!.beacon = preferences.getBoolean("iBeacon", false)
        scanFilter!!.microsoft = preferences.getBoolean("microsoft", false)
        try {
            val pattern = preferences.getString("name", null)
            if (!TextUtils.isEmpty(pattern)) scanFilter!!.name =
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Scan filter invalid name pattern", e)
        }
        try {
            val pattern = preferences.getString("address", null)
            if (!TextUtils.isEmpty(pattern)) scanFilter!!.address =
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Scan filter invalid address pattern", e)
        }
        try {
            val pattern = preferences.getString("advData", null)
            if (!TextUtils.isEmpty(pattern)) scanFilter!!.advData =
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Scan filter invalid advertising data pattern", e)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onScanResult(event: CodelessEvent.ScanResult) {
        if (scanner !== event.scanner) return
        val device = event.device
        val advData = event.advData
        val name = if (advData.name != null) advData.name else CodelessUtil.getName(device)
        val description: String = getDeviceDescription(advData)
        val rssi = event.rssi
        if (bluetoothDeviceList?.contains(device) == true) {
            val index: Int = bluetoothDeviceList!!.indexOf(device)
            deviceList?.set(
                index,
                ScanItem(getDeviceIcon(advData), name, device.address, description, rssi)
            )
            advDataList?.set(index, advData)
            updateList(false)
        } else {
            bluetoothDeviceList?.add(device)
            deviceList?.add(
                ScanItem(
                    getDeviceIcon(advData),
                    name,
                    device.address,
                    description,
                    rssi
                )
            )
            advDataList?.add(advData)
            updateList(true)
        }
    }

    private fun getDeviceDescription(advData: AdvData): String {
        val description = java.util.ArrayList<String>()
        if (advData.codeless) description.add(getString(R.string.scan_codeless))
        if (advData.dsps) description.add(getString(R.string.scan_dsps))
        if (advData.suota) description.add(getString(R.string.scan_suota))
        if (advData.iot) description.add(getString(R.string.scan_iot))
        if (advData.wearable) description.add(getString(R.string.scan_wearable))
        if (advData.mesh) description.add(getString(R.string.scan_mesh))
        if (advData.proximity) description.add(getString(R.string.scan_proximity))
        if (advData.iBeacon || advData.dialogBeacon) description.add(
            getString(
                R.string.scan_beacon,
                advData.beaconUuid.toString(),
                advData.beaconMajor,
                advData.beaconMinor
            )
        )
        if (advData.microsoft) description.add(getString(R.string.scan_microsoft))
        val text = StringBuilder()
        for (i in description.indices) {
            if (i > 0) text.append(", ")
            text.append(description[i])
        }
        return text.toString()
    }

    private fun getDeviceIcon(advData: AdvData): Int {
        if (advData.codeless || advData.dsps) return R.drawable.scan_icon_dsps
        if (advData.suota) return R.drawable.scan_icon_suota
        return if (advData.other()) R.drawable.icon_dialog else R.drawable.icon_device_unknown
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onScanRestart(event: ScanRestart) {
        if (scanner !== event.scanner) return
        startDeviceScan()
    }

    private fun startDeviceScan() {
        if (!scanner!!.checkScanRequirements(
                this,
                REQUEST_ENABLE_BLUETOOTH,
                permissionChecker
            )
        ) return
        if (scanning) stopDeviceScan()
        Log.d(TAG, "Start scanning")
        scanning = true
        bluetoothDeviceList?.clear()
        deviceList?.clear()
        filterBluetoothDeviceList?.clear()
        filterDeviceList?.clear()
        advDataList?.clear()
        bluetoothScanAdapter?.clear()
        bluetoothScanAdapter?.notifyDataSetChanged()
        scanner!!.startScanning()
        handler?.postDelayed(scanTimer!!, SCAN_DURATION.toLong())
        invalidateOptionsMenu()
    }

    private fun stopDeviceScan() {
        handler?.removeCallbacks(delayedListUpdate)
        if (!scanning) return
        Log.d(TAG, "Stop scanning")
        scanning = false
        handler?.removeCallbacks(scanTimer!!)
        scanner!!.stopScanning()
        invalidateOptionsMenu()
    }

    private val delayedListUpdate = Runnable { updateList(true) }

    private fun updateList(force: Boolean) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastListUpdate
        if (elapsed < 0 || elapsed > LIST_UPDATE_INTERVAL || force) {
            handler!!.removeCallbacks(delayedListUpdate)
            lastListUpdate = now
            applyScanFilter()
            bluetoothScanAdapter!!.notifyDataSetChanged()
        } else {
            handler!!.postDelayed(delayedListUpdate, LIST_UPDATE_INTERVAL - elapsed)
        }
    }

    private fun applyScanFilter() {
        for (i in bluetoothDeviceList!!.indices) {
            val device = bluetoothDeviceList!![i]
            val scanItem = deviceList!![i]
            val advData = advDataList!![i]
            val index = filterBluetoothDeviceList!!.indexOf(device)
            var add = false
            if (advData.codeless && scanFilter!!.codeless) add = true
            if (!add && advData.dsps && scanFilter!!.dsps) add = true
            if (!add && advData.suota && scanFilter!!.suota) add = true
            if (!add && advData.other() && scanFilter!!.other) add = true
            if (!add && advData.unknown() && scanFilter!!.unknown) add = true
            if (!add && (advData.iBeacon || advData.dialogBeacon) && scanFilter!!.beacon) add = true
            if (!add && advData.microsoft && scanFilter!!.microsoft) add = true
            if (add && scanFilter!!.name != null) add =
                scanFilter!!.name!!.matcher(if (scanItem.name != null) scanItem.name else "")
                    .matches()
            if (add && scanFilter!!.address != null) add =
                scanFilter!!.address!!.matcher(scanItem.address).matches()
            if (add && scanFilter!!.advData != null) add =
                scanFilter!!.advData!!.matcher(CodelessUtil.hex(advData.raw)).matches()
            if (scanItem.signal < scanFilter!!.rssi && add && index == -1) continue
            if (add) {
                if (index == -1) {
                    filterBluetoothDeviceList!!.add(device)
                    filterDeviceList!!.add(scanItem)
                } else {
                    filterDeviceList!![index] = scanItem
                }
            } else if (index != -1) {
                filterBluetoothDeviceList!!.remove(device)
                filterDeviceList!!.removeAt(index)
            }
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        stopDeviceScan()
        if (pendingConnection != null) return
        pendingConnection = filterBluetoothDeviceList!![position]
        deviceListView!!.onItemClickListener = null
        //val intent = Intent(this@ScanActivity, DeviceActivity::class.java)
        //intent.putExtra("device", pendingConnection)
        //intent.putExtra("name", filterDeviceList!![position].name)
        //startActivity(intent)
    }
}