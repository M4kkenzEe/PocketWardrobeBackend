package com.example.di

import com.example.database.data.repository.RevokedTokenRepositoryImpl
import com.example.database.domain.repository.RevokedTokenRepository
import org.koin.dsl.module

val authModule = module {
    factory<RevokedTokenRepository> { RevokedTokenRepositoryImpl() }
}
