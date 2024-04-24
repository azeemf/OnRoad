package com.onroad.app.ui.search

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.maps.android.PolyUtil
import com.onroad.app.R
import com.onroad.app.databinding.ActivitySearchBinding
import com.onroad.app.ui.base.BaseActivity
import com.onroad.app.ui.navigation.NavigationActivity
import com.onroad.app.util.CommonUtils
import com.onroad.app.util.goToActivity
import javax.inject.Inject

class SearchActivity : BaseActivity(), SearchContract.View, OnMapReadyCallback {

    @Inject
    lateinit var presenter: SearchContract.Presenter<SearchContract.View>

    private lateinit var binding: ActivitySearchBinding;

    private lateinit var googleMap: GoogleMap;
    private var placesClient: PlacesClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(layoutInflater);
        setContentView(binding.root);

        activityComponent.inject(this);
        presenter.attachView(this);

        initView();
    }

    override fun initView() {
        binding.ivClose.setOnClickListener {
            finish()
        }

        binding.directionButton.setOnClickListener {
            goToActivity(NavigationActivity::class.java)
        }

        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }
        // Create a new Places client instance.
        placesClient = Places.createClient(this)

        binding.mapView.onCreate(null);
        binding.mapView.onResume();
        binding.mapView.getMapAsync(this);

        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.place_autocomplete_fragment) as AutocompleteSupportFragment?
        autocompleteFragment!!.setPlaceFields(listOf(
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG)
        )
        //autocompleteFragment!!.setCountry("gb")
        autocompleteFragment.view?.findViewById<EditText>(com.google.android.libraries.places.R.id.places_autocomplete_search_input)?.textSize = 16.0f
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val address = place.address?.toString()
                val latlong = "${place.latLng?.latitude!!}::${place.latLng?.longitude!!}"
                autocompleteFragment.view?.findViewById<EditText>(com.google.android.libraries.places.R.id.places_autocomplete_search_input)?.hint = address
                googleMap.clear()
                showTrackToLocation(place.latLng)
            }

            override fun onError(status: Status) {
                if (status.statusCode != Status.RESULT_CANCELED.statusCode) {
                    Toast.makeText(applicationContext, status.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        })
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
        val currentLocation = CommonUtils.getLocation(this)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLocation!!.latitude, currentLocation!!.longitude), 11.6f))
    }

    private fun showTrackToLocation(address: LatLng) {
        val currentLocation = CommonUtils.getLocation(this)
        showLoading()
        presenter.getTrack(LatLng(currentLocation!!.latitude, currentLocation!!.longitude), address, getString(R.string.google_maps_key))
    }

    override fun drawLine() {
        hideLoading()
        if (dataManager.mapProvider.currentDirections!!.routes!!.isNotEmpty()) {
            val shape =
                dataManager.mapProvider.currentDirections!!.routes?.get(0)?.overviewPolyline?.points
            val polyline = PolylineOptions()
                .addAll(PolyUtil.decode(shape))
                .width(8f)
                .color(Color.BLUE)
            googleMap.addPolyline(polyline)
            binding.directionButton.alpha = 1.0f
            binding.directionButton.isEnabled = true

            val bc = LatLngBounds.Builder()
            for (item in PolyUtil.decode(shape)) {
                bc.include(item)
            }
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bc.build(), 450))
        }
        else {
            Toast.makeText(this, "No routes found", Toast.LENGTH_SHORT).show()
        }
    }
}