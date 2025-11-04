package com.lifetracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.layout.weight // この行は不要になる可能性があります
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetracker.app.core.storage.JsonlStorageLocation

@Composable
fun StorageLocationSelectionScreen(
    onLocationSelected: (JsonlStorageLocation) -> Unit
) {
    val context = LocalContext.current
    val availableLocations = remember {
        JsonlStorageLocation.entries
            .filter { it.resolveDirectory(context) != null }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            // verticalArrangement を削除し、子要素の間隔は個別または LazyColumn で管理
        ) {
            Text(
                text = "保存先の選択",
                style = MaterialTheme.typography.headlineSmall
            )
            // Text間のスペースを確保するためにModifier.paddingを追加
            Text(
                text = "初回起動のため、JSONL ファイルを保存する場所を選択してください。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp) // 上下のテキストとの間隔を調整
            )
            // LazyColumn は Column の中で利用可能な残りのスペースを占める
            LazyColumn(
                // Modifier.weight(1f) を削除
                modifier = Modifier.fillMaxWidth(), // 幅は親に合わせる
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(availableLocations, key = { it.id }) { location ->
                    StorageLocationCard(
                        location = location,
                        onSelect = { onLocationSelected(location) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLocationCard(
    location: JsonlStorageLocation,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = location.displayName(),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = location.descriptionText(),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                modifier = Modifier.align(Alignment.End),
                onClick = onSelect
            ) {
                Text("ここに保存する")
            }
        }
    }
}
