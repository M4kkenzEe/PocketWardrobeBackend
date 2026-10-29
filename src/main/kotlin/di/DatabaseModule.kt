package com.example.di

import com.example.usecases.AdminUserUseCase
import com.example.usecases.ClotheUseCase
import com.example.usecases.EmailVerificationUseCase
import com.example.usecases.GenerateDraftLooksUseCase
import com.example.usecases.GenerateLookUseCase
import com.example.usecases.LookRenderer
import com.example.usecases.SaveLookDraftUseCase
import com.example.usecases.PasswordResetUseCase
import com.example.usecases.QuotaUseCase
import com.example.usecases.RegistrationUseCase
import org.koin.dsl.module
import com.example.database.data.repository.ClotheRepositoryImpl
import com.example.database.data.repository.DailyQuotaRepositoryImpl
import com.example.database.data.repository.EmailVerificationRepositoryImpl
import com.example.database.data.repository.LookDraftRepositoryImpl
import com.example.database.data.repository.PasswordResetRepositoryImpl
import com.example.database.data.repository.PendingEmailVerificationRepositoryImpl
import com.example.database.data.repository.SharedLookRepositoryImpl
import com.example.database.data.repository.UserClotheRepositoryImpl
import com.example.database.data.repository.UserLookRepositoryImpl
import com.example.database.data.repository.UserRepositoryImpl
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.DailyQuotaRepository
import com.example.database.domain.repository.EmailVerificationRepository
import com.example.database.domain.repository.LookDraftRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.PasswordResetRepository
import com.example.database.domain.repository.PendingEmailVerificationRepository
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
    factory<LookDraftRepository> { LookDraftRepositoryImpl() }
    factory<SharedLookRepository> { SharedLookRepositoryImpl() }
    factory<UserClotheRepository> { UserClotheRepositoryImpl() }
    factory<UserLookRepository> { UserLookRepositoryImpl() }
    factory<DailyQuotaRepository> { DailyQuotaRepositoryImpl() }
    factory<PasswordResetRepository> { PasswordResetRepositoryImpl() }
    factory<EmailVerificationRepository> { EmailVerificationRepositoryImpl() }
    factory<PendingEmailVerificationRepository> { PendingEmailVerificationRepositoryImpl() }
    single { ClotheUseCase(lookRepository = get()) }
    single { ImportSharedLookUseCase(sharedLookRepository = get(), clotheRepository = get(), lookRepository = get(), userClotheRepository = get(), userRepository = get()) }
    single { LookRenderer(generateLookService = get(), lookRepository = get()) }
    single { GenerateLookUseCase(generateLookService = get(), lookRenderer = get()) }
    single { GenerateDraftLooksUseCase(generateLookService = get(), userClotheRepository = get(), lookDraftRepository = get(), lookRenderer = get()) }
    single { SaveLookDraftUseCase(lookDraftRepository = get(), lookRepository = get(), userLookRepository = get()) }
    single { QuotaUseCase(userRepository = get()) }
    single { AdminUserUseCase(userRepository = get()) }
    single { EmailVerificationUseCase(emailVerificationRepository = get(), userRepository = get(), emailService = get()) }
    single { RegistrationUseCase(pendingEmailVerificationRepository = get(), userRepository = get(), emailService = get()) }
    single { PasswordResetUseCase(userRepository = get(), passwordResetRepository = get(), emailService = get()) }
}
