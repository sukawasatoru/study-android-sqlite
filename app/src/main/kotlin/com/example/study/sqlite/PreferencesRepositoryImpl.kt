/*
 * Copyright 2023 sukawasatoru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.study.sqlite

import android.content.Context
import androidx.annotation.CheckResult
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface PreferencesRepository {
    fun load(): Flow<Preferences>

    @CheckResult
    suspend fun save(prefs: Preferences): Result<Unit>

    @CheckResult
    suspend fun clear(): Result<Unit>

    @CheckResult
    suspend fun transaction(block: suspend PreferencesRepository.() -> Result<Unit>): Result<Unit>
}

/**
 * Exclusive mode repository.
 */
class PreferencesRepositoryImpl(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PreferencesRepository {
    private val db = Room.databaseBuilder(context, SampleDatabase::class.java, "sample.db")
        .build()
    private val op = InternalRepo()

    override fun load(): Flow<Preferences> {
        return op.load()
            .flowOn(dispatcher)
    }

    override suspend fun save(prefs: Preferences): Result<Unit> {
        return wrapper {
            op.save(prefs)
        }
    }

    override suspend fun clear(): Result<Unit> {
        return wrapper {
            op.clear()
        }
    }

    override suspend fun transaction(block: suspend PreferencesRepository.() -> Result<Unit>): Result<Unit> {
        return wrapper {
            op.block().getOrThrow()
        }
    }

    private suspend fun wrapper(block: suspend () -> Unit): Result<Unit> {
        return withContext(dispatcher) {
            suspendRunCatching {
                db.withTransaction {
                    block()
                }
            }
        }
    }

    private inner class InternalRepo : PreferencesRepository {
        override fun load(): Flow<Preferences> {
            return db
                .prefsDao()
                .load()
                .map { it ?: Preferences.DEFAULT }
                .distinctUntilChanged()
        }

        override suspend fun save(prefs: Preferences): Result<Unit> {
            db.prefsDao().save(prefs)
            return Result.success(Unit)
        }

        override suspend fun clear(): Result<Unit> {
            db.prefsDao().save(Preferences.DEFAULT)
            return Result.success(Unit)
        }

        override suspend fun transaction(block: suspend PreferencesRepository.() -> Result<Unit>): Result<Unit> {
            return Result.failure(UnsupportedOperationException("nested transaction"))
        }
    }
}
