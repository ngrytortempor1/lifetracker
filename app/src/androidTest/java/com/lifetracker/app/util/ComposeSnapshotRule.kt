package com.lifetracker.app.util

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.rules.TestRule

class ComposeSnapshotRule : TestRule {
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            MaterialTheme(content = content)
        }
    }

    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement {
        return composeRule.apply(base, description)
    }
}
