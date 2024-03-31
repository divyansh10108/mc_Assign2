package com.example.myapplication
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
/*
In case fetch button stops working it might be because the api key has expired i am giving a few api keys here just so that it wont be an issue while testing of the code
to be changed on line 128
W5GEFB6GFEN9VTRPSKJEJAXJ7
MJMC9AT7HVN96HMB9N2C2X9CQ
7SM6YFTXKKHWVC94BS9E4K2ME
8SVZRMTKAYZQQLNJG2S7ZV3WL
*/
class MainActivity : ComponentActivity() {
    private val viewModel: WeatherViewModel by lazy { WeatherViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WeatherApp(viewModel)
        }
    }
}

@Composable
fun WeatherApp(viewModel: WeatherViewModel) {
    var cityState by remember { mutableStateOf(TextFieldValue()) }
    var countryState by remember { mutableStateOf(TextFieldValue()) }
    var startDateState by remember { mutableStateOf(TextFieldValue()) }
    var endDateState by remember { mutableStateOf(TextFieldValue()) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = cityState,
            onValueChange = { cityState = it },
            label = { Text("City") },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = countryState,
            onValueChange = { countryState = it },
            label = { Text("Country") },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = startDateState,
            onValueChange = { startDateState = it },
            label = { Text("Start Date (YYYY-MM-DD)") },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = endDateState,
            onValueChange = { endDateState = it },
            label = { Text("End Date (YYYY-MM-DD)") },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = {
                viewModel.fetchWeatherData(
                    city = cityState.text,
                    country = countryState.text,
                    startDate = startDateState.text,
                    endDate = endDateState.text
                )
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Fetch Weather Data")
        }

        Spacer(modifier = Modifier.height(16.dp))

        DisplayWeather(weatherData = viewModel.weatherData)
    }
}

@Composable
fun DisplayWeather(weatherData: LiveData<List<WeatherData>>) {
    val data by weatherData.observeAsState(emptyList())

    if (data.isNotEmpty()) {
        val todayWeather = data.firstOrNull()
        todayWeather?.let {
            Text(
                text = "Max Temperature: ${it.tempmax}°C\nMin Temperature: ${it.tempmin}°C",
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

data class WeatherData(
    val datetime: String,
    val tempmax: Double,
    val tempmin: Double
)

interface ApiService {
    @GET("VisualCrossingWebServices/rest/services/timeline/{city},{country}/{start}/{end}?key=8XYSLXT99H6EBLVUPC7BESWEQ")
    suspend fun getWeatherData(
        @Path("city") city: String,
        @Path("country") country: String,
        @Path("start") start: String,
        @Path("end") end: String
    ): WeatherResponse
}

class WeatherViewModel : ViewModel() {
    private val apiService = Retrofit.Builder()
        .baseUrl("https://weather.visualcrossing.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    private val _weatherData = MutableLiveData<List<WeatherData>>()
    val weatherData: LiveData<List<WeatherData>> = _weatherData

    fun fetchWeatherData(city: String, country: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getWeatherData(city, country, startDate, endDate)
                _weatherData.value = response.days.map {
                    WeatherData(
                        datetime = it.datetime,
                        tempmax = it.tempmax,
                        tempmin = it.tempmin
                    )
                }
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            }
        }
    }
}

data class WeatherResponse(
    @SerializedName("days") val days: List<Day>
)

data class Day(
    val datetime: String,
    val tempmax: Double,
    val tempmin: Double
)
