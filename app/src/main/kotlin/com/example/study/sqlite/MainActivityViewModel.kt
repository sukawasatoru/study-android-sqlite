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
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private fun log(msg: String) {
            Log.i("MainActivityViewModel", msg)
        }
    }

    init {
        log("init")
    }

    override fun onCleared() {
        log("onCleared")
    }

    private val _counter = MutableStateFlow(0)
    val counter: StateFlow<Int> = _counter

    private val dbHelper = TempDbHelper(application) { db ->
        viewModelScope.launch {
            _counter.value = getCounterInternal(db)
        }
    }

    fun getCounter() {
        viewModelScope.launch {
            // https://medium.com/androiddevelopers/threading-models-in-coroutines-and-android-sqlite-api-6cab11f7eb90
            thread {
                val ret = getCounterInternal(dbHelper.readableDatabase)
                log("getCounter: $ret")
            }
        }
    }

    fun setCounter(value: Int) {
        viewModelScope.launch {
            thread {
                log("setCounter begin")
                val db = dbHelper.writableDatabase
                setCounterInternal(db, value)

                _counter.value = getCounterInternal(db)
                log("setCounter end")
            }
        }
    }

    private fun setCounterInternal(db: SQLiteDatabase, value: Int) {
        db.insertWithOnConflict("preferences", "", ContentValues(2).apply {
            put("id", 1)
            put("counter", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private val _loadAndIncrementValue = MutableStateFlow(0)
    val loadAndIncrementValue: StateFlow<Int> = _loadAndIncrementValue
    fun loadAndIncrement() {
        viewModelScope.launch {
            thread {
                log("loadAndIncrement begin")
                _loadAndIncrementValue.value = getCounterInternal(dbHelper.readableDatabase) + 1

                log("loadAndIncrement end")
            }
        }
    }

    fun commitLoadAndIncrementValue() {
        viewModelScope.launch {
            thread {
                log("commitLoadAndIncrementValue begin")
                val db = dbHelper.writableDatabase
                setCounterInternal(db, _loadAndIncrementValue.value)
                _counter.value = getCounterInternal(db)
                log("commitLoadAndIncrementValue end")
            }
        }
    }

    private fun getCounterInternal(db: SQLiteDatabase): Int {
        db.query("preferences", arrayOf("counter"), "id = ?", arrayOf("1"), "", "", "")
            .use { cursor ->
                if (!cursor.moveToFirst()) {
                    return 0
                }

                return cursor.getInt(0)
            }
    }

    fun loadAndIncrementTransaction() {
        viewModelScope.launch {
            thread {
                log("loadAndIncrementTransaction begin")
                val db = dbHelper.writableDatabase

                db.transaction {
                    val newValue = getCounterInternal(this) + 1
                    log("loadAndIncrementTransaction new value: $newValue")

                    try {
                        Thread.sleep(5_000)
                    } catch (e: InterruptedException) {
                        // do nothing.
                    }
                    setCounterInternal(this, newValue)
                }

                _counter.value = getCounterInternal(db)
                log("loadAndIncrementTransaction end")
            }
        }
    }

    class TempDbHelper(context: Context, private val openCb: (db: SQLiteDatabase) -> Unit) :
        SQLiteOpenHelper(context, null, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            log("onCreate")

            db.beginTransaction()
            try {

                db.execSQL("create table preferences(id integer primary key not null, counter integer not null);")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // do nothing.
        }

        override fun onConfigure(db: SQLiteDatabase) {
            log("onConfigure")

            db.enableWriteAheadLogging()
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onOpen(db: SQLiteDatabase) {
            log("onOpen")

            openCb(db)
        }
    }
}
