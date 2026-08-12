package com.example.salarynaftan

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesContractTest {
    private val mainRes = File("src/main/res/xml")

    @Test
    fun backupRules_includeUserDatabasePreferencesAndDataStore() {
        val legacy = File(mainRes, "backup_rules.xml").readText()
        val modern = File(mainRes, "data_extraction_rules.xml").readText()
        listOf("domain=\"database\" path=\"salarynaftan.db\"", "domain=\"sharedpref\" path=\".\"", "domain=\"file\" path=\"datastore/\"")
            .forEach { rule ->
                assertTrue("Missing legacy backup rule: $rule", legacy.contains(rule))
                assertTrue("Missing modern backup rule: $rule", modern.contains(rule))
            }
        assertTrue("Export cache must not be explicitly backed up", !legacy.contains("cache/"))
        assertTrue("Export cache must not be explicitly backed up", !modern.contains("cache/"))
    }

    @Test
    fun manifest_declaresBothBackupRuleFormats() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
    }
}