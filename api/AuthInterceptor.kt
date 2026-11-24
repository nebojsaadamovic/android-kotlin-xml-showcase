package com.example.zdravlje.data.api

import com.example.zdravlje.MainApplication
import com.example.zdravlje.data.sharedPref.PreferenceHelper
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = RetrofitInstance.AUTH_TOKEN
        val language = PreferenceHelper.getSavedLanguage(MainApplication.context!!)
        var lang = language
        if (language == "sr-rRS") {
            lang = "sh"
        }

        val addHeader = chain.request()
            .newBuilder()
            .addHeader("Accept-Language", language)
            .addHeader("lang", lang)

        // Add Authorization only if not set before in request
        if (chain.request().headers["Authorization"] == null) {
            addHeader.addHeader("Authorization", token)
        }

        return chain.proceed(addHeader.build())
    }
}