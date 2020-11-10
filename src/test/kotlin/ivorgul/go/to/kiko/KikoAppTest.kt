package ivorgul.go.to.kiko

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import ivorgul.go.to.kiko.model.*
import ivorgul.go.to.kiko.module.myModule
import ivorgul.go.to.kiko.scheduler.SendNotificationScheduler
import ivorgul.go.to.kiko.service.DAY_IN_MS
import ivorgul.go.to.kiko.type.NotificationType
import ivorgul.go.to.kiko.type.ViewReservationStatus
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Timestamp
import java.util.*
import kotlin.test.assertEquals

class KikoAppTest {

    private val sendNotificationScheduler = mockk<SendNotificationScheduler>()
        .apply { every { startNotificationScheduler() } returns Unit }

    private val objectMapper = jacksonObjectMapper()
    private val rentObjectId = UUID.randomUUID()
    private val newTenantId = UUID.randomUUID()
    private val newTenantAddress = "ivanov@mail.test"
    private val currTenantId = UUID.randomUUID()
    private val currTenantAddress = "+79000000000"
    private val reservationId = UUID.randomUUID()
    private val time = Timestamp(System.currentTimeMillis() + DAY_IN_MS + 1000 * 1000)

    @Test
    fun `create reservation success`() = testApp {
        initData(createReservation = false)

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations?user-id=$newTenantId") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""
                {
                    "objectId": "$rentObjectId",
                    "time": "${System.currentTimeMillis() + DAY_IN_MS + 100 * 1000}"
                }
            """.trimIndent())
        }.apply {
            assertEquals(HttpStatusCode.Created.value, response.status()?.value)
            transaction {
                assertTrue(Notifications.select {
                    Notifications.address.eq(currTenantAddress)
                }.any())
            }
        }
    }

    @Test
    fun `create reservation with incorrect rent object id`() = testApp {
        initData(createReservation = false)

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations?user-id=$newTenantId") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""
                {
                    "objectId": "${UUID.randomUUID()}",
                    "time": "${System.currentTimeMillis() + DAY_IN_MS + 100 * 1000}"
                }
            """.trimIndent())
        }.apply {
            assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
        }
    }

    @Test
    fun `create reservation with time that already reserved`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations?user-id=$newTenantId") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""
                {
                    "objectId": "$rentObjectId",
                    "time": "${objectMapper.writeValueAsString(time)}"
                }
            """.trimIndent())
        }.apply {
            assertEquals(HttpStatusCode.BadRequest.value, response.status()?.value)
        }
    }

    @Test
    fun `create reservation with start time less than one day after now`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations?user-id=$newTenantId") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""
                {
                    "objectId": "$rentObjectId",
                    "time": "${System.currentTimeMillis()}"
                }
            """.trimIndent())
        }.apply {
            assertEquals(HttpStatusCode.BadRequest.value, response.status()?.value)
        }
    }

    @Test
    fun `reject reservation success`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations/$reservationId/reject?user-id=$currTenantId").apply {
            assertEquals(HttpStatusCode.OK.value, response.status()?.value)
            transaction {
                assertTrue(Notifications.select {
                    Notifications.address.eq(newTenantAddress)
                }.any())
                assertEquals(ViewReservationStatus.REJECTED, ViewReservation(
                    ViewReservations.select {
                        ViewReservations.id.eq(reservationId)
                    }.single()
                ).status)
            }
        }
    }

    @Test
    fun `reject reservation fail on incorrect reservation id`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations/${UUID.randomUUID()}/reject?user-id=$currTenantId")
            .apply {
            assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
        }
    }

    @Test
    fun `reject reservation fail for incorrect user`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations/$reservationId/reject?user-id=$newTenantId")
            .apply {
                assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
            }
    }

    @Test
    fun `approve reservation success`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations/$reservationId/approve?user-id=$currTenantId").apply {
            assertEquals(HttpStatusCode.OK.value, response.status()?.value)
            transaction {
                assertTrue(Notifications.select {
                    Notifications.address.eq(newTenantAddress)
                }.any())
                assertEquals(ViewReservationStatus.APPROVED, ViewReservation(
                    ViewReservations.select {
                        ViewReservations.id.eq(reservationId)
                    }.single()
                ).status)
            }
        }
    }

    @Test
    fun `approve reservation fail on incorrect reservation id`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Post, "/view-reservations/${UUID.randomUUID()}/approve?user-id=$currTenantId")
            .apply {
                assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
            }
    }

    @Test
    fun `approve reservation fail on incorrect user`() = testApp {
        initData()
        handleRequest(HttpMethod.Post, "/view-reservations/$reservationId/reject?user-id=$newTenantId")
            .apply {
                assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
            }
    }


    @Test
    fun `cancel reservation success`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Delete, "/view-reservations/$reservationId?user-id=$newTenantId").apply {
            assertEquals(HttpStatusCode.NoContent.value, response.status()?.value)
            transaction {
                assertTrue(Notifications.select {
                    Notifications.address.eq(currTenantAddress)
                }.any())
                assertFalse(ViewReservations.select {
                    ViewReservations.id.eq(reservationId)
                }.any())
            }
        }
    }

    @Test
    fun `cancel reservation fail on incorrect reservation id`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Delete, "/view-reservations/${UUID.randomUUID()}?user-id=$newTenantId")
            .apply {
                assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
            }
    }

    @Test
    fun `cancel reservation fail on incorrect user`() = testApp {
        initData()

        // ACTION
        handleRequest(HttpMethod.Delete, "/view-reservations/$reservationId?user-id=$currTenantId")
            .apply {
                assertEquals(HttpStatusCode.NotFound.value, response.status()?.value)
            }
    }

    @Test
    fun `cancel reservation fail on already rejected reservation`() = testApp {
        initData(reservationStatus = ViewReservationStatus.REJECTED)

        // ACTION
        handleRequest(HttpMethod.Delete, "/view-reservations/$reservationId?user-id=$newTenantId")
            .apply {
                assertEquals(HttpStatusCode.BadRequest.value, response.status()?.value)
            }
    }

    private fun testApp(callback: TestApplicationEngine.() -> Unit) {
        withTestApplication({ myModule(sendNotificationScheduler) }) { callback() }
    }

    private fun initData(
        createReservation: Boolean = true,
        reservationStatus: ViewReservationStatus = ViewReservationStatus.CREATED
    ) {
        transaction {
            Notifications.deleteAll()
            Tenants.insert {
                it[id] = newTenantId
                it[name] = "Иванов Иван Иванович"
                it[contacts] = newTenantAddress
                it[contactsType] = NotificationType.EMAIL.name
            }
            Tenants.insert {
                it[id] = currTenantId
                it[name] = "Петров Пётр Петрович"
                it[contacts] = currTenantAddress
                it[contactsType] = NotificationType.OTHER.name
            }
            RentObjects.insert {
                it[id] = rentObjectId
                it[description] = "Однушка в центре"
                it[currentTenant] = currTenantId
            }
            if (createReservation) {
                ViewReservations.insert {
                    it[id] = reservationId
                    it[timeFrom] = time.time
                    it[timeTo] = time.time + 100
                    it[status] = reservationStatus.name
                    it[currentTenant] = currTenantId
                    it[newTenant] = newTenantId
                    it[objectId] = rentObjectId
                }
            }
        }
    }
}