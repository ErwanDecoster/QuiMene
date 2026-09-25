package com.quimene.sync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.util.UUID

/** Le corps envoyé doit être exactement ce que décode `MatchActivityAttributes.ContentState`
 * (Swift) : mêmes clés, `isStale` présent, clé d'activité en majuscules. */
class LiveActivityPushClientTest {
    @Test
    fun `the push body matches the iOS content state`() {
        val sessionID = UUID.fromString("5a1e2b3c-4d5e-4f60-8172-8394a5b6c7d8")
        val theo = UUID.randomUUID()
        val body =
            Json
                .parseToJsonElement(
                    LiveActivityPushClient.body(
                        LiveActivityPushClient.sessionKey(sessionID),
                        ended = false,
                        LiveActivityContent(
                            UUID.randomUUID(),
                            "Skyjo",
                            "square.grid.3x3.fill",
                            3,
                            listOf(LiveActivityContent.Standing(theo, "Théo", 12)),
                        ),
                    ),
                ).jsonObject
        body["activityKey"]!!.jsonPrimitive.content shouldBe "session:5A1E2B3C-4D5E-4F60-8172-8394A5B6C7D8"
        body["event"]!!.jsonPrimitive.content shouldBe "update"
        val content = body["contentState"]!!.jsonObject
        content.keys shouldBe setOf("matchID", "gameName", "gameSymbol", "roundNumber", "standings", "isStale")
        content["isStale"]!!.jsonPrimitive.boolean shouldBe false
        content["roundNumber"]!!.jsonPrimitive.int shouldBe 3
        content["standings"]!!
            .jsonArray
            .single()
            .jsonObject.keys shouldBe setOf("id", "name", "score")
    }
}
