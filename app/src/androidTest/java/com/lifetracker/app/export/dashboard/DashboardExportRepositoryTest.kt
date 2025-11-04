package com.lifetracker.app.export.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardExportRepositoryTest {

    @Test
    fun save_writesFileAndReturnsSharableUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DashboardExportRepository(context)
        val payload = "metric, value".toByteArray(Charsets.UTF_8)

        val exported = repository.save(
            baseFileName = "dashboard_test",
            format = DashboardExportFormat.CSV,
            bytes = payload
        )

        assertEquals("text/csv", exported.mimeType)
        assertTrue(exported.fileName.endsWith(".csv"))

        val inputStream = ApplicationProvider.getApplicationContext<Context>()
            .contentResolver
            .openInputStream(exported.uri)
        assertNotNull(inputStream)
        inputStream!!.use { stream ->
            val restored = stream.readBytes().toString(Charsets.UTF_8)
            assertEquals("metric, value", restored)
        }
    }
}
