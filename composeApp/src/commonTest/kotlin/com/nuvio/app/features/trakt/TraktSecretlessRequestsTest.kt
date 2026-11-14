package com.nuvio.app.features.trakt

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Trakt apps created since Oct 2026 get no client secret. Device-code sign-in, refresh and revoke
 * must then omit `client_secret` entirely, while requests for apps that do have a secret stay
 * exactly as before. Uses the same Json settings as TraktAuthRepository.
 */
class TraktSecretlessRequestsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private inline fun <reified T> encode(value: T): JsonObject =
        json.parseToJsonElement(json.encodeToString(value)).jsonObject

    @Test
    fun blankSecretMeansNoSecret() {
        assertNull(traktClientSecretOrNull(""))
        assertNull(traktClientSecretOrNull("   "))
        assertEquals("abc", traktClientSecretOrNull("abc"))
    }

    @Test
    fun deviceTokenRequestOmitsSecretWhenAbsent() {
        val body = encode(TraktDeviceTokenRequest(code = "dev", clientId = "id", clientSecret = null))
        assertEquals(setOf("code", "client_id"), body.keys)
        assertEquals("dev", body.getValue("code").jsonPrimitive.content)
        assertEquals("id", body.getValue("client_id").jsonPrimitive.content)
    }

    @Test
    fun deviceTokenRequestUnchangedWithSecret() {
        val body = encode(TraktDeviceTokenRequest(code = "dev", clientId = "id", clientSecret = "s3cret"))
        assertEquals(setOf("code", "client_id", "client_secret"), body.keys)
        assertEquals("s3cret", body.getValue("client_secret").jsonPrimitive.content)
    }

    @Test
    fun refreshRequestOmitsOnlyTheSecretWhenAbsent() {
        val body = encode(
            TraktRefreshTokenRequest(refreshToken = "r", clientId = "id", clientSecret = null, redirectUri = "nuvio://auth/trakt"),
        )
        assertFalse("client_secret" in body)
        assertEquals(setOf("refresh_token", "client_id", "redirect_uri", "grant_type"), body.keys)
        assertEquals("refresh_token", body.getValue("grant_type").jsonPrimitive.content)
    }

    @Test
    fun refreshRequestUnchangedWithSecret() {
        val body = encode(
            TraktRefreshTokenRequest(refreshToken = "r", clientId = "id", clientSecret = "s3cret", redirectUri = "nuvio://auth/trakt"),
        )
        assertEquals(setOf("refresh_token", "client_id", "client_secret", "redirect_uri", "grant_type"), body.keys)
        assertEquals("s3cret", body.getValue("client_secret").jsonPrimitive.content)
    }

    @Test
    fun revokeRequestOmitsSecretWhenAbsentAndKeepsItOtherwise() {
        assertEquals(setOf("token", "client_id"), encode(TraktRevokeRequest(token = "t", clientId = "id", clientSecret = null)).keys)
        assertEquals(
            setOf("token", "client_id", "client_secret"),
            encode(TraktRevokeRequest(token = "t", clientId = "id", clientSecret = "s3cret")).keys,
        )
    }
}
