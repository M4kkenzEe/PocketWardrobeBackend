package com.example.di

import com.example.usecases.ClotheUseCase
import org.koin.dsl.module
import com.example.database.data.repository.ClotheRepositoryImpl
import com.example.database.data.repository.RefreshTokenRepositoryImpl
import com.example.database.data.repository.SharedLookRepositoryImpl
import com.example.database.data.repository.UserClotheRepositoryImpl
import com.example.database.data.repository.UserLookRepositoryImpl
import com.example.database.data.repository.UserRepositoryImpl
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.RefreshTokenRepository
import com.example.database.domain.repository.SharedLookRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.database.domain.repository.UserLookRepository
import com.example.database.domain.repository.UserRepository
import com.example.usecases.ImportSharedLookUseCase
import database.data.repository.LookRepositoryImpl

val databaseModule = module {
    factory<ClotheRepository> { ClotheRepositoryImpl() }
    factory<UserRepository> { UserRepositoryImpl() }
    factory<LookRepository> { LookRepositoryImpl() }
    factory<SharedLookRepository> { SharedLookRepositoryImpl() }
    factory<UserClotheRepository> { UserClotheRepositoryImpl() }
    factory<UserLookRepository> { UserLookRepositoryImpl() }
    factory<RefreshTokenRepository> { RefreshTokenRepositoryImpl() }
    single { ClotheUseCase(lookRepository = get()) }
    single { ImportSharedLookUseCase(sharedLookRepository = get(), clotheRepository = get(), lookRepository = get(), userClotheRepository = get()) }
}