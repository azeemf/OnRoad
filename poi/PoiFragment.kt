package com.onroad.app.ui.poi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import com.bumptech.glide.request.target.CustomTarget
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.onroad.app.databinding.FragmentPoiBinding
import com.onroad.app.ui.base.BaseFragment
import com.onroad.app.ui.component.glide.GlideApp
import com.bumptech.glide.request.transition.Transition
import java.util.Random
import java.util.UUID
import javax.inject.Inject

class PoiFragment : BaseFragment(), PoiContract.View, OnMapReadyCallback {

    @Inject
    lateinit var presenter: PoiContract.Presenter<PoiContract.View>;

    //view que contiene los elementos de este fragment
    lateinit var binding: FragmentPoiBinding;

    //objeto mapa
    private lateinit var googleMap: GoogleMap;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        baseActivity.activityComponent.inject(this);
        presenter.attachFragment(this, baseActivity);
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        //si es la primera vez que se accede al fragment lo creamos
        if (!::binding.isInitialized) {
            binding = FragmentPoiBinding.inflate(layoutInflater, container, false);
        }
        initFragment();
        return binding.root;
    }

    override fun initFragment() {
        //(Objects.requireNonNull(supportFragmentManager.findFragmentById(R.id.fcvMap)) as SupportMapFragment).getMapAsync(this);
        binding.mapView.onCreate(null);
        binding.mapView.onResume();
        binding.mapView.getMapAsync(this@PoiFragment);
    }

    fun isBindingInitialized() = ::binding.isInitialized;

    override fun onMapReady(gogleMap: GoogleMap) {
        googleMap = gogleMap;
        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE)
        if (ActivityCompat.checkSelfPermission(
                baseActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                baseActivity,
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
        addRandomMarkersToMap(baseActivity, googleMap)
    }

    fun addRandomMarkersToMap(context: Context, googleMap: GoogleMap) {
        val random = Random()

        // Generar 4 nombres y apellidos aleatorios
        val names = listOf("John", "Alice", "Bob", "Emily", "David", "Emma", "Michael", "Olivia", "James", "Sophia")
        val surnames = listOf("Smith", "Johnson", "Williams", "Jones", "Brown", "Davis", "Miller", "Wilson", "Moore", "Taylor")

        for (i in 0 until 4) {
            // Generar un nombre aleatorio
            val randomName = names[random.nextInt(names.size)]
            val randomSurname = surnames[random.nextInt(surnames.size)]
            val fullName = "$randomName $randomSurname"

            // URL base de Unsplash para obtener imágenes de avatar
            val unsplashBaseUrl = "https://source.unsplash.com/200x200/?face"

            GlideApp.with(context)
                .asBitmap()
                .load("$unsplashBaseUrl${UUID.randomUUID()}") // Añadimos un UUID aleatorio para obtener una imagen distinta cada vez
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        // Generar ubicaciones aleatorias en el Reino Unido
                        val randomLat = 51.509865 + (Math.random() * (59.612188 - 51.509865))
                        val randomLng = -5.358389 + (Math.random() * (1.749135 - (-5.358389)))
                        val location = LatLng(randomLat, randomLng)

                        // Agregar marcador al mapa
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(location)
                                .title(fullName)
                                .icon(BitmapDescriptorFactory.fromBitmap(resource))
                        )
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // No necesitamos hacer nada aquí
                    }
                })
        }
    }
}