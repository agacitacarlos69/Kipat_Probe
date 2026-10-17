package com.kipat.probe.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * A person the user explicitly chose to keep after a Kipat interaction: a local relationship
 * (id, connectedAt, pinned, source) plus a snapshot of their PUBLIC profile. Never private data:
 * everything here was on their Reveal. One row per public identity (unique [identityId]).
 */
@Entity(tableName = "connections", indices = [Index(value = ["identity_id"], unique = true)])
data class ConnectionEntity(
    @PrimaryKey val id: String,
    /** Their stable public Kipat id (the same for their phone tap, linked card and link). */
    @ColumnInfo(name = "identity_id") val identityId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val headline: String,
    val role: String?,
    val organization: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "profile_revision") val profileRevision: Long?,
    /** Stable Reveal template id (`contact_ripple`, ...), never a display label. */
    @ColumnInfo(name = "reveal_template") val revealTemplate: String,
    /** Accent wire id (`cobalt`, ...). */
    val accent: String,
    /** Authenticity wire id at the time of the snapshot (`unverified`, ...). */
    val authenticity: String,
    @ColumnInfo(name = "connected_at") val connectedAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long?,
    /** ConnectionSource name: PHONE_TAP, NFC_CARD, QR, LINK, EVENT. */
    val source: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
)

/** An encounter (a Reveal of someone), kept separately from Connections. Not a relationship. */
@Entity(tableName = "recent_taps", indices = [Index(value = ["at_millis"])])
data class RecentTapEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "identity_id") val identityId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val subtitle: String,
    val authenticity: String,
    @ColumnInfo(name = "at_millis") val atMillis: Long,
    val source: String,
)

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections")
    fun all(): List<ConnectionEntity>

    @Query("SELECT * FROM connections WHERE identity_id = :identityId LIMIT 1")
    fun byIdentity(identityId: String): ConnectionEntity?

    @Upsert
    fun upsert(entity: ConnectionEntity)

    @Query("DELETE FROM connections WHERE id = :id")
    fun delete(id: String)

    /** One row per identity even if two writers raced: the newer write keeps the identity. */
    @Transaction
    fun save(entity: ConnectionEntity) {
        byIdentity(entity.identityId)?.takeIf { it.id != entity.id }?.let { delete(it.id) }
        upsert(entity)
    }
}

@Dao
interface RecentTapDao {
    @Query("SELECT * FROM recent_taps ORDER BY at_millis DESC LIMIT :limit")
    fun latest(limit: Int): List<RecentTapEntity>

    @Upsert
    fun upsert(entity: RecentTapEntity)

    @Query("DELETE FROM recent_taps WHERE id NOT IN (SELECT id FROM recent_taps ORDER BY at_millis DESC LIMIT :keep)")
    fun trim(keep: Int)

    @Transaction
    fun add(entity: RecentTapEntity, keep: Int) {
        upsert(entity)
        trim(keep)
    }
}

@Database(entities = [ConnectionEntity::class, RecentTapEntity::class], version = 1, exportSchema = true)
abstract class KipatDatabase : RoomDatabase() {
    abstract fun connections(): ConnectionDao
    abstract fun recentTaps(): RecentTapDao

    companion object {
        const val NAME = "kipat.db"
    }
}
