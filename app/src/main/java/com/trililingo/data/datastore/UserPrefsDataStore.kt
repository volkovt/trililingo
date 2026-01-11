package com.trililingo.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.trililingo.datastore.UserPrefs
import java.io.InputStream
import java.io.OutputStream

object UserPrefsDataStore {
    object SerializerImpl : Serializer<UserPrefs> {
        override val defaultValue: UserPrefs = UserPrefs.newBuilder()
            .setStreakCount(0)
            .setLastStudyEpochDay(0)
            .setTotalXp(0)
            .setActiveLanguage("JA")
            .build()

        override suspend fun readFrom(input: InputStream): UserPrefs {
            try {
                return UserPrefs.parseFrom(input)
            } catch (ex: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read proto.", ex)
            }
        }

        override suspend fun writeTo(t: UserPrefs, output: OutputStream) {
            t.writeTo(output)
        }
    }
}
