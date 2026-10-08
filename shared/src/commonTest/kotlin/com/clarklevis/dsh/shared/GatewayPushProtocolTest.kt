package com.clarklevis.dsh.shared

import com.clarklevis.dsh.shared.gateway.GatewayRequest
import com.clarklevis.dsh.shared.gateway.GatewayRequestLanePolicy
import com.clarklevis.dsh.shared.gateway.GatewayPushPlatform
import com.clarklevis.dsh.shared.gateway.GatewayPushRegistration
import com.clarklevis.dsh.shared.gateway.GatewayRequests
import com.clarklevis.dsh.shared.protocol.wireJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayPushProtocolTest {
    private fun payload(request: GatewayRequest): JsonObject =
        wireJson.parseToJsonElement(request.payload).jsonObject

    @Test fun platformRoundTripsItsWireValue() {
        assertEquals("apns", GatewayPushPlatform.APNS.wireValue)
        assertEquals("fcm", GatewayPushPlatform.FCM.wireValue)
        assertEquals(GatewayPushPlatform.APNS, GatewayPushPlatform.fromWire("apns"))
        assertEquals(GatewayPushPlatform.FCM, GatewayPushPlatform.fromWire("fcm"))
        assertEquals(null, GatewayPushPlatform.fromWire("wns"))
        assertEquals(null, GatewayPushPlatform.fromWire(null))
    }

    @Test fun registrationNeverPrintsItsToken() {
        // The delivery address must not reach logs through string interpolation.
        val rendered = GatewayPushRegistration(GatewayPushPlatform.APNS, "a".repeat(64)).toString()
        assertFalse(rendered.contains("a".repeat(64)))
        assertTrue(rendered.contains("APNS"))
    }

    @Test fun registerCarriesThePlatformAndToken() {
        val request = GatewayRequests.registerPush(GatewayPushRegistration(GatewayPushPlatform.FCM, "fcm-token"))
        assertEquals("push-register", request.requestType)
        assertEquals("push-registered", request.responseKind)
        val body = payload(request)
        assertEquals("push-register", body["type"]!!.jsonPrimitive.content)
        assertEquals("fcm", body["platform"]!!.jsonPrimitive.content)
        assertEquals("fcm-token", body["token"]!!.jsonPrimitive.content)
    }

    @Test fun unregisterUsesItsOwnRequestAndResponse() {
        val request = GatewayRequests.unregisterPush(GatewayPushRegistration(GatewayPushPlatform.APNS, "b".repeat(64)))
        assertEquals("push-unregister", request.requestType)
        assertEquals("push-unregistered", request.responseKind)
        assertEquals("apns", payload(request)["platform"]!!.jsonPrimitive.content)
    }

    @Test fun registrationIsNotCoalescedWithOtherPendingWork() {
        // `REJECT_IF_BUSY` keeps a token rotation from being silently dropped behind a
        // slow in-flight request; the client re-sends on every reconnect regardless.
        val request = GatewayRequests.registerPush(GatewayPushRegistration(GatewayPushPlatform.APNS, "c".repeat(64)))
        assertEquals(
            GatewayRequestLanePolicy.REJECT_IF_BUSY,
            request.lanePolicy
        )
    }

    @Test fun registrationDoesNotClaimASession() {
        // Push is gateway-scoped, not session-scoped: one address serves every session.
        val request = GatewayRequests.registerPush(GatewayPushRegistration(GatewayPushPlatform.FCM, "t"))
        assertEquals(null, request.targetSessionId)
    }
}