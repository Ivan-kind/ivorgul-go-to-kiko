package ivorgul.go.to.kiko

import io.ktor.application.Application
import ivorgul.go.to.kiko.module.myModule
import ivorgul.go.to.kiko.scheduler.SendNotificationScheduler

fun Application.main() {
    myModule(SendNotificationScheduler())
}