package com.linode.manager.data.repository

import com.google.gson.Gson
import com.linode.manager.data.remote.ApiErrorResponse
import com.linode.manager.data.remote.firstMessage
import retrofit2.Response

sealed interface ApiResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : ApiResult<T>

    data class Err(
        val message: String,
        val code: Int = -1,
        val unauthorized: Boolean = false,
    ) : ApiResult<Nothing>
}

/** True for auth failures: invalid/expired token (401) or insufficient scope/permission (403). */
fun ApiResult.Err.isAuthFailure(): Boolean = code == 401 || code == 403

private val gson = Gson()

fun <T> Response<T>.toResult(): ApiResult<T> {
    if (isSuccessful) {
        val b = body()
        if (b != null) return ApiResult.Ok(b)
        return ApiResult.Err("Empty response", code())
    }
    val msg =
        try {
            val raw = errorBody()?.string()
            if (raw.isNullOrBlank()) {
                "Request failed (${code()})"
            } else {
                try {
                    gson.fromJson(raw, ApiErrorResponse::class.java)?.firstMessage("Request failed (${code()})")
                        ?: "Request failed (${code()})"
                } catch (_: Exception) {
                    "Request failed (${code()})"
                }
            }
        } catch (_: Exception) {
            "Request failed (${code()})"
        }
    return ApiResult.Err(msg, code(), unauthorized = code() == 401)
}

fun Response<Unit>.toUnitResult(): ApiResult<Unit> {
    if (isSuccessful) return ApiResult.Ok(Unit)
    val r: ApiResult<Unit> = toResult()
    return r
}
