package com.orbitalsonic.googlemapproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.karumi.dexter.BuildConfig
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    // location last updated time
    private var mLastUpdateTime: String? = null

    // location updates interval - 10sec
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

    // fastest updates interval - 5 sec
    // location updates will be received if another app is requesting the locations
    // than your app can handle
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = 3000

    private val REQUEST_CHECK_SETTINGS = 100

    private val TAG = "MainActivity"

    private lateinit var mMap: GoogleMap

    // bunch of location related apis
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mSettingsClient: SettingsClient? = null
    private var mLocationRequest: LocationRequest? = null
    private var mLocationSettingsRequest: LocationSettingsRequest? = null
    private var mLocationCallback: LocationCallback? = null
    private var mCurrentLocation: Location? = null

    // boolean flag to toggle the ui
    private var mRequestingLocationUpdates: Boolean? = null

    private lateinit var txtLocationResult: TextView
    private lateinit var txtUpdatedOn: TextView
    private lateinit var txtLocDistance: TextView
    private lateinit var btnStartUpdates: Button
    private lateinit var btnStopUpdates: Button
    private var totalDistance: Float = 0.0F

    private var marker: MarkerOptions? = null

    private var locList: ArrayList<LatLng> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // initialize the necessary libraries
        initViews()
        initValues()
        onClickMethod()

        // restore the values from saved instance state
        restoreValuesFromBundle(savedInstanceState)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        // Add a marker in Sydney and move the camera
//        val islamabad = LatLng(33.684422, 73.047882)
//        mMap.setMinZoomPreference(15.0f)
//        mMap.setMaxZoomPreference(20.0f)
//        marker?.position(islamabad)
//        marker?.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_walking))
//        mMap.addMarker(marker!!)
//        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(islamabad, 17F))


        getCurrentLocation()
    }

    private fun routeDraw(loc: LatLng) {
//        mMap.addMarker(MarkerOptions().position(loc).title("Islamabad"))
//        val marker = MarkerOptions().position(loc).title("Islamabad")
//        marker.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_dot))

        locList.add(loc)
        mMap.clear()

        if (locList.size >= 2) {
            totalDistance += getDistance(
                locList[locList.size - 2].latitude,
                locList[locList.size - 2].longitude,
                locList[locList.size - 1].latitude,
                locList[locList.size - 1].longitude
            )
        }

        txtLocDistance.text = String.format("Distance %.2f km", convertMeterToKm(totalDistance))

        convertMeterToKm(totalDistance)

        val route: Polyline = mMap.addPolyline(PolylineOptions().width(12F).color(Color.BLUE))
        route.points = locList
        marker?.position(loc)
        mMap.addMarker(marker!!)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 17F))


    }

    /***
     * Returns the approximate distance in meters
     * between this location and the given location.
     * Distance is defined using the WGS84 ellipsoid.
     */
    private fun getDistance(
        startLat: Double,
        startLang: Double,
        endLat: Double,
        endLang: Double
    ): Float {
        val locStart = Location("")
        locStart.latitude = startLat
        locStart.longitude = startLang
        val locEnd = Location("")
        locEnd.latitude = endLat
        locEnd.longitude = endLang
        return locStart.distanceTo(locEnd)
    }

    private fun convertMeterToKm(meter: Float): Float {
        return meter / 1000
    }


    private fun initViews() {
        txtLocationResult = findViewById(R.id.location_result)
        txtUpdatedOn = findViewById(R.id.updated_on)
        txtLocDistance = findViewById(R.id.loc_distance)
        btnStartUpdates = findViewById(R.id.btn_start_location_updates)
        btnStopUpdates = findViewById(R.id.btn_stop_location_updates)
    }

    private fun initValues() {
        marker = MarkerOptions()
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSettingsClient = LocationServices.getSettingsClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // location is received
                mCurrentLocation = locationResult.lastLocation
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                updateLocationUI()
            }
        }
        mRequestingLocationUpdates = false
        mLocationRequest = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            isWaitForAccurateLocation = true
        }

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        mLocationSettingsRequest = builder.build()
    }

    private fun onClickMethod() {
        btnStartUpdates.setOnClickListener {
            startLocationButtonClick()
        }
        btnStopUpdates.setOnClickListener {
            stopLocationButtonClick()
        }

    }

    private fun getCurrentLocation() {
        mSettingsClient
            ?.checkLocationSettings(mLocationSettingsRequest!!)
            ?.addOnSuccessListener(this) {

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
                }
//                mFusedLocationClient!!.requestLocationUpdates(
//                    mLocationRequest!!,
//                    mLocationCallback!!, Looper.myLooper()!!
//                )

                mFusedLocationClient!!.getCurrentLocation(
                    LocationRequest.PRIORITY_HIGH_ACCURACY,
                    null
                )
                mFusedLocationClient!!.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            txtLocationResult.text =
                                "Lat: ${location!!.latitude}, Lng: ${location!!.longitude}"

                            marker?.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_walking))
                            routeDraw(LatLng(location.latitude, location.longitude))

                            // giving a blink animation on TextView
                            txtLocationResult.alpha = 0f
                            txtLocationResult.animate().alpha(1f).duration = 300

                            // location last updated time
                            mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                            txtUpdatedOn.text = "Last updated on: $mLastUpdateTime"
                        }
                    }
                updateLocationUI()

            }
            ?.addOnFailureListener(this) { e ->
                val statusCode = (e as ApiException).statusCode
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        Log.i(
                            TAG,
                            "Location settings are not satisfied. Attempting to upgrade " +
                                    "location settings "
                        )
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the
                            // result in onActivityResult().
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(
                                this,
                                REQUEST_CHECK_SETTINGS
                            )
                        } catch (sie: IntentSender.SendIntentException) {
                            Log.i(
                                TAG,
                                "PendingIntent unable to execute request."
                            )
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage = "Location settings are inadequate, and cannot be " +
                                "fixed here. Fix in Settings."
                        Log.e(TAG, errorMessage)
                    }
                }
                updateLocationUI()
            }
    }

    /**
     * Restoring values from saved instance state
     */
    private fun restoreValuesFromBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("is_requesting_updates")) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean("is_requesting_updates")
            }
            if (savedInstanceState.containsKey("last_known_location")) {
                mCurrentLocation = savedInstanceState.getParcelable("last_known_location")
            }
            if (savedInstanceState.containsKey("last_updated_on")) {
                mLastUpdateTime = savedInstanceState.getString("last_updated_on")
            }
        }
        updateLocationUI()
    }


    /**
     * Update the UI displaying the location data
     * and toggling the buttons
     */
    @SuppressLint("SetTextI18n")
    private fun updateLocationUI() {
        if (mCurrentLocation != null) {
            txtLocationResult.text =
                "Lat: ${mCurrentLocation!!.latitude}, Lng: ${mCurrentLocation!!.longitude}"

            routeDraw(LatLng(mCurrentLocation!!.latitude, mCurrentLocation!!.longitude))

            // giving a blink animation on TextView
            txtLocationResult.alpha = 0f
            txtLocationResult.animate().alpha(1f).duration = 300

            // location last updated time
            txtUpdatedOn.text = "Last updated on: $mLastUpdateTime"
        }
        toggleButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_requesting_updates", (mRequestingLocationUpdates)!!)
        outState.putParcelable("last_known_location", mCurrentLocation)
        outState.putString("last_updated_on", mLastUpdateTime)
    }

    private fun toggleButtons() {
        if ((mRequestingLocationUpdates)!!) {
            btnStartUpdates.isEnabled = false
            btnStopUpdates.isEnabled = true
        } else {
            btnStartUpdates.isEnabled = true
            btnStopUpdates.isEnabled = false
        }
    }

    /**
     * Starting location updates
     * Check whether location settings are satisfied and then
     * location updates will be requested
     */
    private fun startLocationUpdates() {
        mSettingsClient
            ?.checkLocationSettings(mLocationSettingsRequest!!)
            ?.addOnSuccessListener(this) {
                Log.i(TAG, "All location settings are satisfied.")
                showMessage("Started location updates!")

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
                }
                mFusedLocationClient!!.requestLocationUpdates(
                    mLocationRequest!!,
                    mLocationCallback!!, Looper.myLooper()!!
                )
                updateLocationUI()
            }
            ?.addOnFailureListener(this) { e ->
                val statusCode = (e as ApiException).statusCode
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        Log.i(
                            TAG,
                            "Location settings are not satisfied. Attempting to upgrade " +
                                    "location settings "
                        )
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the
                            // result in onActivityResult().
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(
                                this,
                                REQUEST_CHECK_SETTINGS
                            )
                        } catch (sie: IntentSender.SendIntentException) {
                            Log.i(
                                TAG,
                                "PendingIntent unable to execute request."
                            )
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage = "Location settings are inadequate, and cannot be " +
                                "fixed here. Fix in Settings."
                        Log.e(TAG, errorMessage)
                    }
                }
                updateLocationUI()
            }
    }


    private fun startLocationButtonClick() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    mRequestingLocationUpdates = true
                    startLocationUpdates()
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    if (response.isPermanentlyDenied) {
                        // open device settings when the permission is
                        // denied permanently
                        openSettings()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).check()
    }

    private fun stopLocationButtonClick() {
        mRequestingLocationUpdates = false
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        // Removing location updates
        mFusedLocationClient
            ?.removeLocationUpdates(mLocationCallback!!)
            ?.addOnCompleteListener(this) {
                showMessage("Location updates stopped!")
                toggleButtons()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> Log.e(
                    TAG,
                    "User agreed to make required location settings changes."
                )
                RESULT_CANCELED -> {
                    Log.e(
                        TAG,
                        "User chose not to make required location settings changes."
                    )
                    mRequestingLocationUpdates = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Resuming location updates depending on button state and
        // allowed permissions
        if ((mRequestingLocationUpdates)!! && checkPermissions()) {
            startLocationUpdates()
        }
        updateLocationUI()
    }

    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }


    override fun onPause() {
        super.onPause()
        if ((mRequestingLocationUpdates)!!) {
            // pausing location updates
            stopLocationUpdates()
        }
    }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts(
            "package",
            BuildConfig.APPLICATION_ID, null
        )
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}