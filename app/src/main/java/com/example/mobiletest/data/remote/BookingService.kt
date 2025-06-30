package com.example.mobiletest.data.remote

import com.example.mobiletest.model.Booking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 网络接口
 *
 * Created by RuanJunHao on 2025/6/29.
 */
class BookingService @Inject constructor() {
    suspend fun fetchBookingData(): Booking {
        delay(1000)
        val json = """
            {
                "shipReference": "ABCDEF",
                "shipToken": "AAAABBBCCCCDDD",
                "canIssueTicketChecking": false,
                "expiryTime": "1722409261",
                "duration": 2430,
                "segments": [
                    {
                        "id": 1,
                        "originAndDestinationPair": {
                            "origin": {"code": "AAA", "displayName": "AAA DisplayName", "url": "www.ship.com"},
                            "originCity": "BBB",
                            "destination": {"code": "BBB", "displayName": "BBB DisplayName", "url": "www.ship.com"},
                            "destinationCity": "AAA"
                        }
                    },
                    {
                        "id": 2,
                        "originAndDestinationPair": {
                            "origin": {"code": "BBB", "displayName": "BBB DisplayName", "url": "www.ship.com"},
                            "originCity": "BBB",
                            "destination": {"code": "CCC", "displayName": "CCC DisplayName", "url": "www.ship.com"},
                            "destinationCity": "CCC"
                        }
                    }
                ]
            }
        """.trimIndent()
        return Json.decodeFromString(Booking.serializer(), json)
    }
}