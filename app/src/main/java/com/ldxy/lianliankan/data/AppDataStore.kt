package com.ldxy.lianliankan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 应用级 DataStore 实例（SRS FR-14.1）。
 *
 * 用扩展属性委托，保证整个进程内只有一个实例 —— DataStore 明确要求同一文件
 * 不能有多个活动实例，否则会抛异常。
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreKeys.STORE_NAME,
)
