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
        assertEquals("mipush", GatewayPushPlatform.MIPUSH.wireValue)
        assertEquals("vivopush", GatewayPushPlatform.VIVOPUSH.wireValue)
        assertEquals(GatewayPushPlatform.APNS, GatewayPushPlatform.fromWire("apns"))
        assertEquals(GatewayPushPlatform.FCM, GatewayPushPlatform.fromWire("fcm"))
        assertEquals(GatewayPushPlatform.MIPUSH, GatewayPushPlatform.fromWire("mipush"))
        assertEquals(GatewayPushPlatform.VIVOPUSH, GatewayPushPlatform.fromWire("vivopush"))
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

    // --- the client's "notify me in the background" switch -------------------
    //
    // FCM/APNs `notification` payloads are rendered by the OS, so the app cannot
    // filter them locally; the value has to travel to the gateway.

    @Test fun registerCarriesTheBackgroundNotificationPreference() {
        val off = GatewayRequests.registerPush(
            GatewayPushRegistration(GatewayPushPlatform.FCM, "fcm-token", notifyInBackground = false)
        )
        assertEquals(false, payload(off)["notifyInBackground"]!!.jsonPrimitive.content.toBoolean())

        val on = GatewayRequests.registerPush(
            GatewayPushRegistration(GatewayPushPlatform.FCM, "fcm-token", notifyInBackground = true)
        )
        assertEquals(true, payload(on)["notifyInBackground"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun registerOmitsThePreferenceWhenItIsUnknown() {
        // A caller that does not know the preference must not send `false`, which
        // would mute the device; omitting it lets the gateway apply its default.
        val request = GatewayRequests.registerPush(GatewayPushRegistration(GatewayPushPlatform.FCM, "fcm-token"))
        assertFalse(payload(request).containsKey("notifyInBackground"))
    }

    @Test fun unregisterDoesNotCarryThePreference() {
        // Unregister matches on platform+token; a preference field would be noise.
        val request = GatewayRequests.unregisterPush(
            GatewayPushRegistration(GatewayPushPlatform.APNS, "d".repeat(64), notifyInBackground = false)
        )
        assertFalse(payload(request).containsKey("notifyInBackground"))
    }

    @Test fun thePreferenceIsStillRedactedFromLogs() {
        // Adding a field must not undo the token redaction.
        val rendered = GatewayPushRegistration(GatewayPushPlatform.FCM, "e".repeat(64), notifyInBackground = false).toString()
        assertFalse(rendered.contains("e".repeat(64)))
        assertTrue(rendered.contains("notifyInBackground=false"))
    }
}