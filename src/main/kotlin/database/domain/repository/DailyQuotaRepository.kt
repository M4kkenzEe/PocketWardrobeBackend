package com.example.database.domain.repository

import kotlinx.datetime.LocalDate

interface DailyQuotaRepository {
    suspend fun getOrCreate(userId: Int, date: LocalDate): Int
    suspend fun increment(userId: Int, date: LocalDate)
    suspend fun getCount(userId: Int, date: LocalDate): Int
}
