package com.example.myapplication

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate.parse
/*
In case fetch button stops working it might be because the api key has expired i am giving a few api keys here just so that it wont be an issue while testing of the code
to be changed on line 128
W5GEFB6GFEN9VTRPSKJEJAXJ7
MJMC9AT7HVN96HMB9N2C2X9CQ
7SM6YFTXKKHWVC94BS9E4K2ME
8SVZRMTKAYZQQLNJG2S7ZV3WL
*/
@Entity(tableName = "weather_data", primaryKeys = ["date", "latitude", "longitude"])
data class WeatherEntry(
    val date: String,
    val latitude: Double,
    val longitude: Double,
    val maxTemperature: Double,
    val minTemperature: Double
)

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Adjust insertion behavior
    suspend fun insertWeatherData(weather: WeatherEntry)

    @androidx.room.Query("SELECT * FROM weather_data WHERE date = :date AND latitude = :latitude AND longitude = :longitude")
    suspend fun getWeatherByDate(date: String, latitude: Double, longitude: Double): WeatherEntry?
}

@Database(entities = [WeatherEntry::class], version = 3, exportSchema = false)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null

        fun getDatabase(context: Context): WeatherDatabase? {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        WeatherDatabase::class.java,
                        "weather_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {

                    INSTANCE = null
                    null
                }
            }
        }
    }
}


interface WeatherService {
    @GET("archive")
    fun getWeatherData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String
    ): Call<WeatherData>
}

interface ForecastService {
    @GET("forecast")
    fun getWeatherData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String,
    ): Call<WeatherData>
}


data class WeatherData(
    val latitude: Double,
    val longitude: Double,
    val generationTimeMs: Double,
    val utcOffsetSeconds: Int,
    val timezone: String,
    val timezone_abbreviation: String?,
    val elevation: Int,
    val daily_units: DailyUnits?,
    val daily: Daily
)

data class DailyUnits(
    val time: String,
    val temperature_2m_max: String,
    val temperature_2m_min: String
)

data class Daily(
    val time: List<String>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>
)


class WeatherViewModel(private val application: Application) : ViewModel() {
    private val _weatherData = mutableStateOf<WeatherData?>(null)


    private val database: WeatherDatabase? = WeatherDatabase.getDatabase(application)
    val weatherDao = database?.weatherDao()
    val weatherData: WeatherData? get() = _weatherData.value
    var _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getWeatherFromDatabase(
        date: String,
        latitude: Double,
        longitude: Double
    ): WeatherEntry? {
        if (!isValidDate(date)) {
            _error.value = "Invalid input. Please enter a valid date (MM-DD) and year."
            return null
        }
        val error = validateCoordinates(latitude.toString(), longitude.toString())
        if (error != null) {
            _error.value = error
            return null
        }
        return weatherDao?.getWeatherByDate(
            date,
            roundToTwoDecimals(latitude),
            roundToTwoDecimals(longitude)
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun fetchWeatherData(date: String, latitude: String, longitude: String) {

        if (!isValidDate(date)) {
            _error.value = "Invalid input. Please enter a valid date (MM-DD) and year."
            return
        }
        val error = validateCoordinates(latitude, longitude)
        if (error != null) {
            _error.value = error
            return
        }
        val today = LocalDate.now()
        val tenDaysAgo = today.minusDays(10)
        val requestedDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        if (requestedDate.isAfter(today)) {
            CoroutineScope(Dispatchers.IO).launch {
                val avgTemps = calculateAndDisplayAverageTemperatures(
                    requestedDate,
                    latitude.toDouble(),
                    longitude.toDouble()
                )
                if (avgTemps != null) {
                    _error.value = null
                    _weatherData.value = WeatherData(
                        latitude = latitude.toDouble(),
                        longitude = longitude.toDouble(),
                        generationTimeMs = 0.0,
                        utcOffsetSeconds = 0,
                        timezone = "",
                        timezone_abbreviation = "",
                        elevation = 0,
                        daily_units = DailyUnits("", "", ""),
                        daily = Daily(
                            listOf(date),
                            listOf(avgTemps.first),
                            listOf(avgTemps.second)
                        )
                    )
                    _error.value = null
                } else {
                    _error.value = "Error fetching weather data. Please try again later."

                }

            }

            return
        }
        if (requestedDate.isBefore(tenDaysAgo)) {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://archive-api.open-meteo.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)
            val call = service.getWeatherData(
                latitude = latitude.toDouble(),
                longitude = longitude.toDouble(),
                startDate = date,
                endDate = date,
                daily = "temperature_2m_max,temperature_2m_min"
            )

            call.enqueue(object : Callback<WeatherData> {
                override fun onResponse(call: Call<WeatherData>, response: Response<WeatherData>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.daily.temperature_2m_max.isNotEmpty() && body.daily.temperature_2m_min?.isNotEmpty() == true) {
                            _weatherData.value = body
                            _error.value = null


                            val weatherEntry = WeatherEntry(
                                date = date,
                                latitude = roundToTwoDecimals(latitude.toDouble()), // Round here
                                longitude = roundToTwoDecimals(longitude.toDouble()),
                                maxTemperature = body.daily.temperature_2m_max[0],
                                minTemperature = body.daily.temperature_2m_min[0]
                            )
                            viewModelScope.launch(Dispatchers.IO) {

                                weatherDao?.insertWeatherData(weatherEntry)
                            }

                        } else {
                            _error.value =
                                "Error occurred, temperature data not available for the specified date"
                        }
                    } else {
                        _error.value = "Error fetching weather data. Please try again later."
                    }
                }

                override fun onFailure(call: Call<WeatherData>, t: Throwable) {
                    _error.value = "Network error. Please check your internet connection."
                }
            })
        } else {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(ForecastService::class.java)
            val call = service.getWeatherData(
                latitude = latitude.toDouble(),
                longitude = longitude.toDouble(),
                startDate = date,
                endDate = date,
                daily = "temperature_2m_max,temperature_2m_min"
            )

            call.enqueue(object : Callback<WeatherData> {
                override fun onResponse(call: Call<WeatherData>, response: Response<WeatherData>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.daily.temperature_2m_max.isNotEmpty() && body.daily.temperature_2m_min.isNotEmpty()) {
                            _weatherData.value = body
                            _error.value = null
                            val weatherEntry = WeatherEntry(
                                date = date,
                                latitude = roundToTwoDecimals(latitude.toDouble()),
                                longitude = roundToTwoDecimals(longitude.toDouble()),
                                maxTemperature = body.daily.temperature_2m_max[0],
                                minTemperature = body.daily.temperature_2m_min[0]
                            )
                            viewModelScope.launch(Dispatchers.IO) {

                                weatherDao?.insertWeatherData(weatherEntry)
                            }

                        } else {
                            _error.value =
                                "Error occurred, temperature data not available for the specified date"
                        }
                    } else {
                        _error.value = "Error fetching weather data. Please try again later."
                    }
                }

                override fun onFailure(call: Call<WeatherData>, t: Throwable) {
                    _error.value = "Network error. Please check your internet connection."
                }
            })
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun calculateAndDisplayAverageTemperatures(
        date: LocalDate,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Double>? {
        val historicalDates = getHistoricalDates(date)

        var totalMaxTemp = 0.0
        var totalMinTemp = 0.0
        var count = 0

        for (historicalDate in historicalDates) {
            val weatherEntry = getWeatherFromDatabase(
                historicalDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                latitude,
                longitude
            )

            weatherEntry?.let {
                totalMaxTemp += it.maxTemperature
                totalMinTemp += it.minTemperature
                count++
            }
        }

        return if (count > 0) {
            val avgMaxTemp = totalMaxTemp / count
            val avgMinTemp = totalMinTemp / count
            Pair(roundToTwoDecimals(avgMaxTemp), roundToTwoDecimals(avgMinTemp))
        } else {
            _error.value = "No historical data available to calculate averages."
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getHistoricalDates(date: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        val currentYear = LocalDate.now().year
        for (year in currentYear - 10 until currentYear) {
            dates.add(LocalDate.of(year, date.month, date.dayOfMonth))
        }
        return dates
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun fetchAndStoreHistoricalData(latitude: String, longitude: String): String {
        _error.value= null
        val error = validateCoordinates(latitude, longitude)
        if (error != null) {
            _error.value = error
            return "Invalid coordinates. Please enter valid latitude and longitude."
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val today = LocalDate.now().minusDays(10)
                val tenYearsAgo = today.minusYears(10)
                val bulkWeatherData = fetchHistoricalDataRange(
                    latitude,
                    longitude,
                    tenYearsAgo.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                )

                bulkWeatherData?.daily?.let { dailyData ->
                    for (day in dailyData.time.indices) {
                        val date = dailyData.time[day]
                        val maxTemp = dailyData.temperature_2m_max[day]
                        val minTemp = dailyData.temperature_2m_min[day]

                        val weatherEntry = WeatherEntry(
                            date,
                            latitude.toDouble(),
                            longitude.toDouble(),
                            maxTemp,
                            minTemp
                        )
                        weatherDao?.insertWeatherData(weatherEntry)
                    }
                }

            } catch (e: Exception) {
                _error.value = "Error fetching historical data. Please try again."
            }
        }
        return "10 Years data stored."
    }

    private suspend fun fetchHistoricalDataRange(
        latitude: String,
        longitude: String,
        startDate: String,
        endDate: String
    ): WeatherData? {

        val retrofit = Retrofit.Builder()
            .baseUrl("https://archive-api.open-meteo.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        val call = service.getWeatherData(
            latitude = latitude.toDouble(),
            longitude = longitude.toDouble(),
            startDate = startDate,
            endDate = endDate,
            daily = "temperature_2m_max,temperature_2m_min"
        )

        val response = call.execute()
        if (response.isSuccessful) {
            return response.body()
        } else {
            return null
        }
    }
}



class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = WeatherViewModel(application = application)

        setContent {
            run {
                WeatherApp(viewModel)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WeatherApp(viewModel: WeatherViewModel) {
    var inputDate by remember { mutableStateOf("") }
    var something by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var maxtemp by remember { mutableStateOf("") }
    var mintemp by remember { mutableStateOf("") }
    var weatherData = viewModel.weatherData
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = inputDate,
            onValueChange = { inputDate = it },
            label = { Text("Start Date (yyyy-mm-dd)") }
        )
        OutlinedTextField(
            value = something,
            onValueChange = { something = it },
            label = { Text("End Date (yyyy-mm-dd)") }
        )
        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text(text = "Latitude") }
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text(text = "Longitude") }
        )

        Button(
            onClick = {
                error = null
                try {
                    val parts = inputDate.split("-")
                    if (parts.size == 3) {
                        year = parts[0]
                        date = "${parts[0]}-${parts[1]}-${parts[2]}"
                        CoroutineScope(Dispatchers.IO).launch {
                            val weatherEntry = viewModel.getWeatherFromDatabase(
                                date,
                                latitude.toDouble(),
                                longitude.toDouble()
                            )
                            weatherEntry?.let { weatherEntry ->
                                maxtemp = weatherEntry.maxTemperature.toString()
                                mintemp = weatherEntry.minTemperature.toString()
                                error = null
                            } ?: run {
                                viewModel.fetchWeatherData(date, latitude, longitude)
                                weatherData?.let { weather ->
                                    if (weather.daily.temperature_2m_max?.isNotEmpty() == true &&
                                        weather.daily.temperature_2m_min?.isNotEmpty() == true
                                    ) {
                                        maxtemp = getMaxTemperature(weather)
                                        mintemp = getMinTemperature(weather)
                                    } else {
                                        error =
                                            "Error occurred, temperature data not available for the specified date"
                                    }
                                }
                            }
                        }
                    } else {
                        error = "Invalid date format. Please enter date in yyyy-mm-dd format."
                    }
                } catch (e: Exception) {
                    error = "An error occurred. Please try again later."
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Fetch Weather Data")
        }

        Button(
            onClick = {
                error = viewModel.fetchAndStoreHistoricalData(latitude, longitude)
            }, modifier = Modifier.align(Alignment.End)
        ) {
            Text("Fetch 10 years")
        }

        weatherData = viewModel.weatherData
        if (weatherData != null) {
            maxtemp = getMaxTemperature(weatherData!!)
            mintemp = getMinTemperature(weatherData!!)
            error = null
        }

        error?.let {
            Text(it)
        } ?: run {
            Column {
                Text(
                    "Max Temp: ${maxtemp}°C",
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Min Temp: ${mintemp}°C",
                )
            }
        }
    }
}

fun getMaxTemperature(weatherData: WeatherData): String {
    return weatherData.daily.temperature_2m_max[0].toString()
}

fun getMinTemperature(weatherData: WeatherData): String {
    return weatherData.daily.temperature_2m_min[0].toString()

}




fun validateCoordinates(latitude: String, longitude: String): String? {
    val latitudeDouble = latitude.toDoubleOrNull()
    val longitudeDouble = longitude.toDoubleOrNull()

    if (latitudeDouble == null) {
        return "Invalid latitude format"
    }
    if (longitudeDouble == null) {
        return "Invalid longitude format"
    }
    if (latitudeDouble !in -90.0..90.0) {
        return "Latitude must be within -90 to 90 degrees"
    }
    if (longitudeDouble !in -180.0..180.0) {
        return "Longitude must be within -180 to 180 degrees"
    }

    return null
}

fun roundToTwoDecimals(value: Double): Double {
    return String.format("%.2f", value).toDouble()
}

@RequiresApi(Build.VERSION_CODES.O)
fun isValidDate(date: String): Boolean {
    return try {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        parse(date, formatter)
        true
    } catch (e: Exception) {
        false
    }
}
