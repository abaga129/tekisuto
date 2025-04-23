package com.abaga129.tekisuto.util

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Service for fetching available Azure Text-to-Speech voices
 */
class AzureVoiceService(private val context: Context) {
    companion object {
        private const val TAG = "AzureVoiceService"
        private const val CACHE_EXPIRATION_HOURS = 24 // Cache voices for 24 hours
        private const val VOICES_CACHE_KEY = "azure_voices_cache"
        private const val VOICES_CACHE_TIMESTAMP_KEY = "azure_voices_cache_timestamp"
    }

    /**
     * Fetches the list of available voices from Azure TTS API
     * 
     * @return List of AzureVoiceInfo objects representing available voices
     */
    suspend fun getVoices(): List<AzureVoiceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cachedVoices = getVoicesFromCache()
                if (cachedVoices.isNotEmpty()) {
                    Log.d(TAG, "Using cached voices list (${cachedVoices.size} voices)")
                    return@withContext cachedVoices
                }

                // Get API key and region from preferences
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                val apiKey = sharedPreferences.getString("azure_speech_key", "") ?: ""
                val region = sharedPreferences.getString("azure_speech_region", "eastus") ?: "eastus"
                
                Log.d(TAG, "Fetching voices from Azure Speech API (region: $region)")
                
                if (apiKey.isEmpty()) {
                    Log.e(TAG, "Azure Speech API key not configured")
                    return@withContext emptyList<AzureVoiceInfo>()
                }
                
                // Construct the URL for the voices endpoint
                val url = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list")
                
                // Create and configure the connection
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 15000 // 15 seconds
                
                // Check the response code
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error code: $responseCode")
                    return@withContext emptyList<AzureVoiceInfo>()
                }
                
                // Read the response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                // Parse the JSON response
                val voices = parseVoicesResponse(response.toString())
                
                // Cache the results
                cacheVoices(voices)
                
                Log.d(TAG, "Successfully fetched ${voices.size} voices from Azure")
                return@withContext voices
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching voices", e)
                return@withContext emptyList<AzureVoiceInfo>()
            }
        }
    }
    
    /**
     * Parse the JSON response from the Azure TTS API
     * 
     * @param jsonResponse The JSON response string
     * @return List of AzureVoiceInfo objects
     */
    private fun parseVoicesResponse(jsonResponse: String): List<AzureVoiceInfo> {
        val voices = mutableListOf<AzureVoiceInfo>()
        
        try {
            val jsonArray = JSONArray(jsonResponse)
            
            for (i in 0 until jsonArray.length()) {
                val voiceObject = jsonArray.getJSONObject(i)
                
                val name = voiceObject.getString("Name")
                val displayName = voiceObject.getString("DisplayName")
                val localName = voiceObject.getString("LocalName")
                val shortName = voiceObject.getString("ShortName")
                val gender = voiceObject.getString("Gender")
                val locale = voiceObject.getString("Locale")
                val voiceType = voiceObject.getString("VoiceType")
                val status = voiceObject.optString("Status", "GA")
                
                // WordsPerMinute may not be present in all voice data
                val wordsPerMinute = if (voiceObject.has("WordsPerMinute")) {
                    voiceObject.getInt("WordsPerMinute")
                } else {
                    null
                }
                
                voices.add(
                    AzureVoiceInfo(
                        name = name,
                        displayName = displayName,
                        localName = localName,
                        shortName = shortName,
                        gender = gender,
                        locale = locale,
                        voiceType = voiceType,
                        status = status,
                        wordsPerMinute = wordsPerMinute
                    )
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON response", e)
        }
        
        return voices
    }
    
    /**
     * Cache the list of voices
     * 
     * @param voices List of voices to cache
     */
    private fun cacheVoices(voices: List<AzureVoiceInfo>) {
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = sharedPreferences.edit()
            
            // Convert the list to a JSON string
            val jsonArray = JSONArray()
            for (voice in voices) {
                val voiceObject = JSONObject().apply {
                    put("name", voice.name)
                    put("displayName", voice.displayName)
                    put("localName", voice.localName)
                    put("shortName", voice.shortName)
                    put("gender", voice.gender)
                    put("locale", voice.locale)
                    put("voiceType", voice.voiceType)
                    put("status", voice.status)
                    if (voice.wordsPerMinute != null) {
                        put("wordsPerMinute", voice.wordsPerMinute)
                    }
                }
                jsonArray.put(voiceObject)
            }
            
            // Save the JSON string and current timestamp
            editor.putString(VOICES_CACHE_KEY, jsonArray.toString())
            editor.putLong(VOICES_CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
            editor.apply()
            
            Log.d(TAG, "Cached ${voices.size} voices")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching voices", e)
        }
    }
    
    /**
     * Get the cached list of voices if not expired
     * 
     * @return List of cached voices or empty list if cache is expired or invalid
     */
    private fun getVoicesFromCache(): List<AzureVoiceInfo> {
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            
            // Check if cache exists and is not expired
            val timestamp = sharedPreferences.getLong(VOICES_CACHE_TIMESTAMP_KEY, 0)
            val currentTime = System.currentTimeMillis()
            val cacheAge = TimeUnit.MILLISECONDS.toHours(currentTime - timestamp)
            
            if (timestamp == 0L || cacheAge >= CACHE_EXPIRATION_HOURS) {
                Log.d(TAG, "Voices cache is expired or not found")
                return emptyList()
            }
            
            val jsonString = sharedPreferences.getString(VOICES_CACHE_KEY, null) ?: return emptyList()
            val voices = mutableListOf<AzureVoiceInfo>()
            
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val voiceObject = jsonArray.getJSONObject(i)
                
                val wordsPerMinute = if (voiceObject.has("wordsPerMinute")) {
                    voiceObject.getInt("wordsPerMinute")
                } else {
                    null
                }
                
                voices.add(
                    AzureVoiceInfo(
                        name = voiceObject.getString("name"),
                        displayName = voiceObject.getString("displayName"),
                        localName = voiceObject.getString("localName"),
                        shortName = voiceObject.getString("shortName"),
                        gender = voiceObject.getString("gender"),
                        locale = voiceObject.getString("locale"),
                        voiceType = voiceObject.getString("voiceType"),
                        status = voiceObject.getString("status"),
                        wordsPerMinute = wordsPerMinute
                    )
                )
            }
            
            return voices
        } catch (e: Exception) {
            Log.e(TAG, "Error reading voices from cache", e)
            return emptyList()
        }
    }
    
    /**
     * Clear the voices cache
     */
    fun clearCache() {
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = sharedPreferences.edit()
            editor.remove(VOICES_CACHE_KEY)
            editor.remove(VOICES_CACHE_TIMESTAMP_KEY)
            editor.apply()
            Log.d(TAG, "Voices cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing voices cache", e)
        }
    }
}