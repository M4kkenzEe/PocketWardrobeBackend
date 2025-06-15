package com.example.auth.di

import com.example.auth.service.JwtService
import org.koin.dsl.module

val authModule = module {
    single { JwtService() }
}