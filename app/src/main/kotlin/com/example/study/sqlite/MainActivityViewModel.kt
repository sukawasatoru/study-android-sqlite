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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private fun log(msg: String) {
            Log.i("MainActivityViewModel", msg)
        }
    }

    private val prefsRepo: PreferencesRepository = PreferencesRepositoryImpl(application)

    init {
        log("init")
    }

    override fun onCleared() {
        log("onCleared")
    }

    val counter: StateFlow<Preferences> = prefsRepo
        .load()
        .catch {
            log("failed to load prefs: $it")
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Preferences.DEFAULT)

    fun getCounter() {
        viewModelScope.launch {
            val ret = suspendRunCatching {
                prefsRepo.load().first().counter
            }.getOrElse {
                log("getCounter $it")
                return@launch
            }
            log("getCounter: $ret")
        }
    }

    fun clearPrefs() {
        viewModelScope.launch {
            prefsRepo.clear().getOrElse {
                log("clearPrefs: $it")
            }
        }
    }

    private val _loadAndIncrementValue = MutableStateFlow(Preferences.DEFAULT)
    val loadAndIncrementValue: StateFlow<Preferences> = _loadAndIncrementValue
    fun loadAndIncrement() {
        viewModelScope.launch {
            log("loadAndIncrement begin")
            val current = suspendRunCatching {
                prefsRepo.load().first()
            }.getOrElse {
                log("loadAndIncrement $it")
                return@launch
            }
            _loadAndIncrementValue.value = current.copy(
                counter = current.counter + 1
            )

            log("loadAndIncrement end")
        }
    }

    fun commitLoadAndIncrementValue() {
        viewModelScope.launch {
            log("commitLoadAndIncrementValue begin")
            prefsRepo.save(_loadAndIncrementValue.value)
                .getOrElse {
                    log("commitLoadAndIncrementValue $it")
                    return@launch
                }
            log("commitLoadAndIncrementValue end")
        }
    }

    fun loadAndIncrementTransaction() {
        viewModelScope.launch {
            log("loadAndIncrementTransaction begin")
            prefsRepo.transaction {
                val current = load().first()
                val newValue = current.copy(
                    counter = current.counter + 1
                )
                log("loadAndIncrementTransaction new value: $newValue")

                delay(5_000.milliseconds)
                save(newValue)
            }.getOrElse {
                log("loadAndIncrementTransaction $it")
                return@launch
            }
            log("loadAndIncrementTransaction end")
        }
    }
}
