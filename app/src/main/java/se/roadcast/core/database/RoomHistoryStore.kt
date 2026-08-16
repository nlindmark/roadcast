package se.roadcast.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "played_places")
data class PlayedPlaceEntity(
    @PrimaryKey val placeId: String,
    val playedAtEpochMillis: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT placeId FROM played_places ORDER BY playedAtEpochMillis ASC")
    fun observePlaceIds(): kotlinx.coroutines.flow.Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayedPlaceEntity)

    @Query("DELETE FROM played_places")
    suspend fun clear()
}

@Database(
    entities = [PlayedPlaceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RoadcastDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

@Singleton
class RoomHistoryStore @Inject constructor(
    private val dao: HistoryDao,
) : HistoryStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val playedPlaceIds: StateFlow<Set<String>> = dao.observePlaceIds()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override suspend fun markPlayed(placeId: String) {
        dao.upsert(
            PlayedPlaceEntity(
                placeId = placeId,
                playedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clear() {
        dao.clear()
    }
}
