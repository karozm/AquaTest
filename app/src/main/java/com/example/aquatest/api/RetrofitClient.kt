package com.example.aquatest.api

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://g230dvyule.execute-api.eu-north-1.amazonaws.com/prod/"

    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val response = chain.proceed(request)

            try {
                val peekBody = response.peekBody(Long.MAX_VALUE)
                val bodyString = peekBody.string()

                val requestUrl = request.url().toString()
                val statusCode = response.code()
                val statusMessage = response.message()

                Log.d("Retrofit", "Response from $requestUrl")
                Log.d("Retrofit", "Status: $statusCode $statusMessage")
                Log.d("Retrofit", "Body: $bodyString")
            } catch (e: Exception) {
                Log.e("Retrofit", "Error logging response", e)
            }

            return response
        }
    }

    val instance: ApiService by lazy {
        val okHttpClient =
                okhttp3.OkHttpClient.Builder().addInterceptor(LoggingInterceptor()).build()

        // Create Gson with lenient mode to handle large certificate strings
        val gson = GsonBuilder().setLenient().create()

        val retrofit =
                Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .build()

        retrofit.create(ApiService::class.java)
    }
}
