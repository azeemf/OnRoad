package com.onroad.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.Html
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.api.Http
import com.google.maps.android.PolyUtil
import com.onroad.app.R
import com.onroad.app.data.model.directions.Step
import com.onroad.app.databinding.ActivityNavigationBinding
import com.onroad.app.ui.base.BaseActivity
import com.onroad.app.util.CommonUtils
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class NavigationActivity : BaseActivity(), NavigationContract.View, OnMapReadyCallback,
    LocationListener {

    @Inject
    lateinit var presenter: NavigationContract.Presenter<NavigationContract.View>

    private lateinit var binding: ActivityNavigationBinding;

    private lateinit var googleMap: GoogleMap;
    private lateinit var locationManager: LocationManager

    private var remainingSteps: MutableList<Step> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        binding = ActivityNavigationBinding.inflate(layoutInflater);
        setContentView(binding.root);

        activityComponent.inject(this);
        presenter.attachView(this);

        initView();
    }

    override fun initView() {

        binding.ivClose.setOnClickListener {
            finish()
        }

        binding.mapView.onCreate(null);
        binding.mapView.onResume();
        binding.mapView.getMapAsync(this)

        remainingSteps = dataManager.mapProvider.currentDirections!!.routes!!.first()!!.legs!!.first()!!.steps as MutableList<Step>
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Solicitar actualizaciones de ubicación
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0,
            0f,
            this
        )
        locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            0,
            0f,
            this
        )
    }

    override fun onMapReady(gogleMap: GoogleMap) {
        googleMap = gogleMap;
        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        googleMap.setMyLocationEnabled(true)
        onLocationChanged(CommonUtils.getLocation(this)!!)
    }

    override fun onLocationChanged(location: Location) {
        // Calcular velocidad en mph
        val speed = location.speed * 2.236942 // Convertir de m/s a mph
        if (speed.toInt() > 49) { binding.progressBar.progress = 49 }
        if (speed.toInt() == 0) { binding.progressBar.progress = 1 }
        else { binding.progressBar.progress = speed.toInt() }

        val remainingDistance = footDistance(location, remainingSteps[0].endLocation!!.lat!!, remainingSteps[0]?.endLocation!!.lng!!)

        if (remainingDistance < 65) {
            remainingSteps.remove(remainingSteps.first())
        }

        binding.distanceText.text = String.format("%.1f ft.", remainingDistance)

        reloadData()
    }

    private fun reloadData() {
        googleMap.clear()
        val shape = remainingSteps.get(0)?.polyline?.points
        val polyline = PolylineOptions()
            .addAll(PolyUtil.decode(shape))
            .width(8f)
            .color(Color.BLUE)
        googleMap.addPolyline(polyline)

        val bc = LatLngBounds.Builder()
        for (item in PolyUtil.decode(shape)) {
            bc.include(item)
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bc.build(), 400))

        if (!remainingSteps.first().htmlInstructions.isNullOrEmpty()) {
            binding.nextDirectionText.text = Html.fromHtml(remainingSteps.first().htmlInstructions)
        }
        else {
            binding.nextDirectionText.text = "--"
        }
        if (!remainingSteps.first().maneuver.isNullOrEmpty()) {
            binding.imageNext.setImageResource(resources.getIdentifier(remainingSteps.first().maneuver, "drawable", packageName))
        }
        else {
            binding.imageNext.setImageDrawable(getDrawable(R.drawable.straight))
        }
    }

    private fun footDistance(myLocation: Location, destLatitude: Double, destLongitude: Double): Double {
        val earthRadius = 20925524.9 // Radio de la Tierra en pies

        val deltaLatitud = Math.toRadians(destLatitude - myLocation.latitude)
        val deltaLongitud = Math.toRadians(destLongitude - myLocation.longitude)

        val a = sin(deltaLatitud / 2) * sin(deltaLatitud / 2) +
                cos(Math.toRadians(myLocation.latitude)) * cos(Math.toRadians(destLatitude)) *
                sin(deltaLongitud / 2) * sin(deltaLongitud / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val footDistance = earthRadius * c

        return footDistance
    }
}