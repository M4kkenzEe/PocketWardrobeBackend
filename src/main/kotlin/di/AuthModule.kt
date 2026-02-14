package com.example.di

import com.example.auth.service.JwtService
import com.example.auth.service.TokenService
import org.koin.dsl.module

val authModule = module {
    single { JwtService() }  // Keep for backward compatibility
    single { TokenService() }
}