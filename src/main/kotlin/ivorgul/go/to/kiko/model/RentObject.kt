package ivorgul.go.to.kiko.model

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import java.util.*

object RentObjects : Table() {
    val id = uuid("id")
    val description = varchar("description", 255)
    val currentTenant = uuid("current_tenant")

    override val primaryKey = PrimaryKey(id, name = "PK_rent_objects")
}

data class RentObject(
    val id: UUID,
    val currentTenant: UUID,
    val description: String
) {
    constructor(row: ResultRow) : this(
        id = row[RentObjects.id],
        currentTenant = row[RentObjects.currentTenant],
        description = row[RentObjects.description]
    )
}