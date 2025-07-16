package com.example.di

import org.koin.dsl.module
import com.example.database.data.repository.ClotheRepositoryImpl
import com.example.database.data.repository.LookRepositoryImpl
import com.example.database.data.repository.UserRepositoryImpl
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.UserRepository
import com.example.services.RemoveBgService

val databaseModule = module {
    factory<ClotheRepository> { ClotheRepositoryImpl() }
    factory<UserRepository> { UserRepositoryImpl() }
    factory<LookRepository> { LookRepositoryImpl() }
    single { RemoveBgService() }
}