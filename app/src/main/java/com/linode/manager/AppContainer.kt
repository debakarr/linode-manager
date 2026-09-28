package com.linode.manager

import android.content.Context
import com.linode.manager.data.AppSettings
import com.linode.manager.data.DeviceKeyStore
import com.linode.manager.data.TokenStore
import com.linode.manager.data.remote.LinodeApi
import com.linode.manager.data.repository.LinodeRepository
import com.linode.manager.data.ssh.HostKeyStore
import com.linode.manager.data.ssh.SshProfileStore
import com.linode.manager.data.ssh.SshSessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(
    context: Context,
    apiBaseUrl: String = "https://api.linode.com/v4/",
) {
    val tokenStore = TokenStore(context)
    val deviceKeys = DeviceKeyStore(context)
    val hostKeys = HostKeyStore(context)
    val sshProfiles = SshProfileStore(context)
    val sshSessions = SshSessionManager(context, hostKeys)
    val settings = AppSettings(context)

    private val authInterceptor =
        Interceptor { chain ->
            val token = tokenStore.readToken().orEmpty()
            val req =
                if (token.isBlank()) {
                    chain.request()
                } else {
                    chain
                        .request()
                        .newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build()
                }
            chain.proceed(req)
        }

    private val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()

    private val api: LinodeApi =
        Retrofit
            .Builder()
            .baseUrl(apiBaseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LinodeApi::class.java)

    val repository = LinodeRepository(api)
}
