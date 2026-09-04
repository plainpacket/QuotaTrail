package app.quotatrail.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.quotatrail.R
import app.quotatrail.presentation.theme.QuotaTrailTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FontLicensesButton(modifier: Modifier = Modifier) {
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { showLicenses = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_font_licenses))
    }

    if (showLicenses) {
        val resources = LocalResources.current
        val licenseText by produceState<String?>(initialValue = null, resources) {
            value = withContext(Dispatchers.IO) {
                resources.openRawResource(R.raw.font_licenses)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_font_licenses)) },
            text = {
                SelectionContainer {
                    Text(
                        text = licenseText ?: stringResource(R.string.settings_font_licenses_loading),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(R.string.settings_font_licenses_close))
                }
            },
        )
    }
}

@Preview
@Composable
private fun FontLicensesButtonPreview() {
    QuotaTrailTheme { FontLicensesButton() }
}
