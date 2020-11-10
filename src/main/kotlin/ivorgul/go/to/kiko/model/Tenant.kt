package ivorgul.go.to.kiko.model

import ivorgul.go.to.kiko.type.NotificationType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import java.util.*

object Tenants : Table() {
    val id = uuid("id")
    val name = varchar("name", 255)
    val contacts = varchar("contancts", 255)
    val contactsType = varchar("contacts_type", 50)

    override val primaryKey = PrimaryKey(id, name = "PK_tenants")
}

data class Tenant(
    val id: UUID,
    val name: String,
    val contacts: String,
    val contactsType: NotificationType
) {
    constructor(row: ResultRow) : this(
        id = row[Tenants.id],
        name = row[Tenants.name],
        contacts = row[Tenants.contacts],
        contactsType = NotificationType.valueOf(row[Tenants.contactsType])
    )
}