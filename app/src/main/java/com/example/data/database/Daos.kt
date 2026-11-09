package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestigationDao {
    @Query("SELECT * FROM investigations ORDER BY timestamp DESC")
    fun getAllInvestigations(): Flow<List<InvestigationEntity>>

    @Query("SELECT * FROM investigations WHERE id = :id LIMIT 1")
    fun getInvestigationById(id: String): Flow<InvestigationEntity?>

    @Query("SELECT * FROM investigations WHERE type = :type ORDER BY timestamp DESC")
    fun getInvestigationsByType(type: String): Flow<List<InvestigationEntity>>

    @Query("SELECT * FROM investigations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentInvestigations(limit: Int = 10): Flow<List<InvestigationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestigation(entity: InvestigationEntity)

    @Update
    suspend fun updateInvestigation(entity: InvestigationEntity)

    @Delete
    suspend fun deleteInvestigation(entity: InvestigationEntity)

    @Query("DELETE FROM investigations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM investigations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM investigations")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM investigations WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM investigations WHERE status = 'RUNNING'")
    fun getRunningCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM investigations WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>
}

@Dao
interface FindingDao {
    @Query("SELECT * FROM findings WHERE investigationId = :investigationId ORDER BY id ASC")
    fun getFindingsForInvestigation(investigationId: String): Flow<List<FindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<FindingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: FindingEntity)

    @Query("DELETE FROM findings WHERE investigationId = :investigationId")
    suspend fun deleteByInvestigationId(investigationId: String)
}

@Dao
interface ScamReportDao {
    @Query("SELECT * FROM scam_reports ORDER BY reportDate DESC")
    fun getAllReports(): Flow<List<ScamReportEntity>>

    @Query("SELECT * FROM scam_reports WHERE phoneNumber LIKE '%' || :query || '%' ORDER BY reportDate DESC")
    fun searchReports(query: String): Flow<List<ScamReportEntity>>

    @Query("SELECT * FROM scam_reports WHERE phoneNumber = :phoneNumber ORDER BY reportDate DESC")
    fun getReportsForNumber(phoneNumber: String): Flow<List<ScamReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ScamReportEntity)

    @Query("DELETE FROM scam_reports WHERE id = :id")
    suspend fun deleteReport(id: Long)

    @Query("SELECT COUNT(*) FROM scam_reports")
    fun getTotalCount(): Flow<Int>
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}
