package com.connectx.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.connectx.app.data.local.dao.ChatDao
import com.connectx.app.data.local.dao.ContactDao
import com.connectx.app.data.local.dao.MessageDao
import com.connectx.app.data.local.entity.ChatEntity
import com.connectx.app.data.local.entity.ContactEntity
import com.connectx.app.data.local.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, ChatEntity::class, ContactEntity::class, com.connectx.app.data.local.entity.CallLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ConnectXDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun contactDao(): ContactDao
    abstract fun callLogDao(): com.connectx.app.data.local.dao.CallLogDao
}
