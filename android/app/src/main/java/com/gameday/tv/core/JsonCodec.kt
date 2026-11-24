package com.gameday.tv.core

import kotlinx.serialization.json.Json

object JsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}
