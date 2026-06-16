package com.locationtracker.app.utils

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object LocationUtils {

    private const val TAG = "LocationUtils"

    // Master function — tries 3 methods
    suspend fun getPlaceName(
        context: Context,
        lat: Double,
        lng: Double
    ): String {

        // METHOD 1: Google Places Nearby Search
        val placesName = tryPlacesApi(
            context, lat, lng)
        if (!placesName.isNullOrBlank()) {
            Log.d(TAG, "Places API: $placesName")
            return placesName
        }

        // METHOD 2: Nominatim (OpenStreetMap)
        // Free, no API key needed
        val nominatimName = tryNominatim(
            lat, lng)
        if (!nominatimName.isNullOrBlank()) {
            Log.d(TAG, "Nominatim: $nominatimName")
            return nominatimName
        }

        // METHOD 3: Android Geocoder
        val geocoderName = tryGeocoder(
            context, lat, lng)
        if (!geocoderName.isNullOrBlank()) {
            Log.d(TAG, "Geocoder: $geocoderName")
            return geocoderName
        }

        Log.w(TAG, "All geocoding failed for " +
            "$lat, $lng")
        return "Unknown location"
    }

    suspend fun getPlaceAddress(
        context: Context,
        lat: Double,
        lng: Double
    ): String {
        return try {
            tryGeocoderAddress(context, lat, lng)
                ?: ""
        } catch (e: Exception) { "" }
    }

    // ─────────────────────────────────────
    // METHOD 1: Google Places Nearby Search
    // Returns closest landmark name
    // ─────────────────────────────────────
    private suspend fun tryPlacesApi(
        context: Context,
        lat: Double,
        lng: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = getMapsApiKey(context)
            if (apiKey.isBlank()) {
                Log.w(TAG, "No Maps API key found")
                return@withContext null
            }

            val url = "https://maps.googleapis" +
                ".com/maps/api/place/" +
                "nearbysearch/json" +
                "?location=$lat,$lng" +
                "&rankby=distance" +
                "&type=establishment" +
                "&key=$apiKey"

            val response = httpGet(url)
                ?: return@withContext null

            val json = JSONObject(response)
            val status = json.optString("status")

            Log.d(TAG, "Places status: $status")

            if (status != "OK")
                return@withContext null

            val results = json
                .optJSONArray("results")
                ?: return@withContext null
            if (results.length() == 0)
                return@withContext null

            val first = results.getJSONObject(0)
            val name = first.optString("name", "")
            if (name.isBlank())
                return@withContext null

            // Get distance to this place
            val loc = first
                .optJSONObject("geometry")
                ?.optJSONObject("location")
            val pLat = loc?.optDouble("lat") ?: lat
            val pLng = loc?.optDouble("lng") ?: lng

            val dist = FloatArray(1)
            android.location.Location
                .distanceBetween(
                    lat, lng, pLat, pLng, dist)
            val d = dist[0].toInt()

            when {
                d <= 50  -> name
                d <= 200 -> "Near $name"
                else     -> "Near $name"
            }

        } catch (e: Exception) {
            Log.e(TAG,
                "Places API failed: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────
    // METHOD 2: Nominatim (OpenStreetMap)
    // No API key, works everywhere in India
    // ─────────────────────────────────────
    private suspend fun tryNominatim(
        lat: Double,
        lng: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim." +
                "openstreetmap.org/reverse" +
                "?lat=$lat&lon=$lng" +
                "&format=json" +
                "&addressdetails=1" +
                "&zoom=18"

            // Nominatim requires a User-Agent
            val response = httpGet(
                url,
                userAgent = "MyApplication/1.0"
            ) ?: return@withContext null

            val json = JSONObject(response)
            val address = json
                .optJSONObject("address")
                ?: return@withContext null

            // Priority: most specific first
            // amenity = named building/shop
            // road = street name
            // neighbourhood = area
            // suburb = suburb
            // town / city = city level

            val amenity = address
                .optString("amenity", "")
            val shop = address
                .optString("shop", "")
            val office = address
                .optString("office", "")
            val building = address
                .optString("building", "")
            val road = address
                .optString("road", "")
            val neighbourhood = address
                .optString("neighbourhood", "")
            val suburb = address
                .optString("suburb", "")
            val village = address
                .optString("village", "")
            val town = address
                .optString("town", "")
            val city = address
                .optString("city", "")

            // Return most specific non-empty value
            val primary = listOf(
                amenity, shop, office,
                building
            ).firstOrNull { it.isNotBlank() }

            if (!primary.isNullOrBlank()) {
                // Named place found
                return@withContext primary
            }

            // No named building — use area name
            val area = listOf(
                neighbourhood, suburb,
                village, town, city
            ).firstOrNull { it.isNotBlank() }

            area?.takeIf { it.isNotBlank() }

        } catch (e: Exception) {
            Log.e(TAG,
                "Nominatim failed: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────
    // METHOD 3: Android Geocoder fallback
    // ─────────────────────────────────────
    private suspend fun tryGeocoder(
        context: Context,
        lat: Double,
        lng: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(
                context, Locale.getDefault())
            val results = geocoder
                .getFromLocation(lat, lng, 1)
            val addr = results
                ?.firstOrNull()
                ?: return@withContext null

            // Never return featureName —
            // it gives raw building codes
            // Use area names only
            addr.subLocality
                ?.takeIf { it.isNotBlank() }
                ?: addr.locality
                    ?.takeIf { it.isNotBlank() }
                ?: addr.subAdminArea
                    ?.takeIf { it.isNotBlank() }
                ?: addr.adminArea
                    ?.takeIf { it.isNotBlank() }

        } catch (e: Exception) {
            Log.e(TAG,
                "Geocoder failed: ${e.message}")
            null
        }
    }

    // Address subtitle — "Patna, Bihar"
    private suspend fun tryGeocoderAddress(
        context: Context,
        lat: Double,
        lng: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(
                context, Locale.getDefault())
            val results = geocoder
                .getFromLocation(lat, lng, 1)
            val addr = results
                ?.firstOrNull()
                ?: return@withContext null

            buildString {
                addr.locality
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(it) }
                addr.adminArea
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        if (isNotEmpty())
                            append(", ")
                        append(it)
                    }
            }.takeIf { it.isNotBlank() }

        } catch (e: Exception) { null }
    }

    // ─────────────────────────────────────
    // HTTP GET helper
    // ─────────────────────────────────────
    private fun httpGet(
        urlString: String,
        userAgent: String = "Android"
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlString)
                .openConnection()
                as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent", userAgent)
            connection.setRequestProperty(
                "Accept-Language", "en")

            if (connection.responseCode != 200) {
                Log.w(TAG, "HTTP ${connection
                    .responseCode} for $urlString")
                return null
            }

            connection.inputStream
                .bufferedReader()
                .readText()

        } catch (e: Exception) {
            Log.e(TAG,
                "httpGet failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    // ─────────────────────────────────────
    // Read Maps API key from resources
    // ─────────────────────────────────────
    private fun getMapsApiKey(
        context: Context
    ): String {
        return try {
            val resId = context.resources
                .getIdentifier(
                    "google_maps_key",
                    "string",
                    context.packageName)
            if (resId != 0)
                context.getString(resId)
            else ""
        } catch (e: Exception) { "" }
    }
}
