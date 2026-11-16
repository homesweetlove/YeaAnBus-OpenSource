package kr.yeaan.bus

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import kr.yeaan.bus.data.local.BusDatabase
import kr.yeaan.bus.data.remote.*
import kr.yeaan.bus.data.repository.BusRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class YeaAnBusApp : Application() {
    lateinit var repository: BusRepository
        private set
    override fun onCreate() {
        super.onCreate()
        val gson = Gson()
        val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).build()
        fun retrofit(url: String) = Retrofit.Builder().baseUrl(url).client(http)
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        val db = Room.databaseBuilder(this, BusDatabase::class.java, "yeaan-bus.db").build()
        repository = BusRepository(
            AndongSource(retrofit("https://bus.andong.go.kr/").create(AndongApi::class.java)),
            TagoSource(retrofit("https://apis.data.go.kr/1613000/").create(TagoApi::class.java), kr.yeaan.bus.config.ApiKeys.TAGO_API_KEY),
            db.dao(), gson
        )
    }
}
