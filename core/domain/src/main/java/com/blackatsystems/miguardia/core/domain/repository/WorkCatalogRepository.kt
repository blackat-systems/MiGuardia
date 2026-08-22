package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceAdoption
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceAdoptionResult
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTemplateUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface WorkCatalogRepository {
    fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog>

    fun observeRecentlyUsed(
        timelineId: UUID,
        sector: WorkSector,
        limit: Int = 5,
    ): Flow<List<RecentWorkTemplate>>

    suspend fun getWorkPlace(id: UUID): WorkPlace?
    suspend fun getWorkType(id: UUID): WorkType?
    suspend fun getWorkTemplate(id: UUID): WorkTemplate?
    suspend fun getRuleRevisionAt(workPlaceId: UUID, date: LocalDate): WorkplaceRuleRevision?
    suspend fun getRuleRevisions(workPlaceId: UUID): List<WorkplaceRuleRevision>

    suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet)

    suspend fun createWorkPlace(newWorkPlace: NewWorkPlace)

    suspend fun adoptWorkPlace(adoption: WorkPlaceAdoption): WorkPlaceAdoptionResult

    suspend fun updateWorkPlace(update: WorkPlaceUpdate)

    suspend fun setWorkPlaceActive(
        id: UUID,
        isActive: Boolean,
        updatedAt: Instant,
    )

    suspend fun createWorkType(workType: WorkType)
    suspend fun updateWorkType(update: WorkTypeUpdate)

    suspend fun setWorkTypeActive(
        id: UUID,
        isActive: Boolean,
        updatedAt: Instant,
    )

    suspend fun createWorkTemplate(workTemplate: WorkTemplate)
    suspend fun updateWorkTemplate(update: WorkTemplateUpdate)

    suspend fun setWorkTemplateActive(
        id: UUID,
        isActive: Boolean,
        updatedAt: Instant,
    )

    suspend fun addWorkplaceRuleRevision(
        revision: WorkplaceRuleRevision,
        confirmationNow: Instant,
    )

    suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory
}
