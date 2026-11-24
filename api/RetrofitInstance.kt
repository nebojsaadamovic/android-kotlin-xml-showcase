package com.example.zdravlje.data.api

import android.util.Log
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.example.zdravlje.data.deserializer.TimeslotDeserializer
import com.example.zdravlje.data.model.response.calendar.Timeslot
import com.example.zdravlje.data.sharedPref.PreferenceHelper
import com.example.zdravlje.MainApplication
import com.google.gson.GsonBuilder
import com.nites.ezdravljeapp.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    var AUTH_TOKEN = MainApplication.context.let {  PreferenceHelper.getToken(MainApplication.context!!) } ?: ""

    private var client = OkHttpClient.Builder().apply {
        addInterceptor(AuthInterceptor())
        addInterceptor(
            ChuckerInterceptor.Builder(MainApplication.context!!)
                .collector(ChuckerCollector(MainApplication.context!!))
                .maxContentLength(250000L)
                .redactHeaders(emptySet())
                .alwaysReadResponseBody(false)
                .build()
        )
        Log.d("Auth", AUTH_TOKEN)
        readTimeout(1000, TimeUnit.SECONDS)
        callTimeout(1000, TimeUnit.SECONDS)
        writeTimeout(1000, TimeUnit.SECONDS)
        connectTimeout(1000, TimeUnit.SECONDS)
    }.build()

    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Timeslot::class.java, TimeslotDeserializer()) // <-- dodaj ovo
        .create()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val appApi: AppApi by lazy {
        retrofit.create(AppApi::class.java)
    }
}