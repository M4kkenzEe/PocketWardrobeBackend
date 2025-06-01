package com.example.auth.di

import com.example.auth.AuthController
import com.example.auth.service.JwtService
import com.example.auth.service.UserService
import org.koin.dsl.module

val authModule = module {
    single { JwtService() }
    single { UserService() }
    single { AuthController() }
}