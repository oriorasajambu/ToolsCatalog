package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.feature.qrscan.R

/**
 * A scanned Wi-Fi code, read back.
 *
 * Deliberately not the payment report's tag-by-tag layout. A Wi-Fi code has four facts in it and no
 * structure worth exposing, so showing it as segments would be pedantry — what someone scanning one
 * wants is the network name and the password, in a form they can copy.
 */
@Composable
internal fun WifiReportView(
    credentials: WifiCredentials,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalResources.current

    ReportRowList(
        heading = resources.getString(R.string.qrscan_wifi_heading),
        rows = credentials.rows(resources),
        onCopy = onCopy,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * The rows to show, built once so the list and the shareable text cannot disagree.
 *
 * The security type and the hidden flag are not copyable: they are this app's words for a code's
 * contents, not values anyone would paste anywhere.
 */
private fun WifiCredentials.rows(resources: Resources): List<ReportRow> = buildList {
    add(ReportRow(resources.getString(R.string.qrscan_wifi_network), ssid))
    add(
        ReportRow(
            label = resources.getString(R.string.qrscan_wifi_security),
            value = resources.getString(security.labelRes()),
            copyable = false,
            monospace = false,
        ),
    )
    if (security != WifiSecurity.OPEN) {
        add(ReportRow(resources.getString(R.string.qrscan_wifi_password), password))
    }
    add(
        ReportRow(
            label = resources.getString(R.string.qrscan_wifi_hidden),
            value = resources.getString(
                if (hidden) R.string.qrscan_wifi_yes else R.string.qrscan_wifi_no,
            ),
            copyable = false,
            monospace = false,
        ),
    )
}

private fun WifiSecurity.labelRes(): Int = when (this) {
    WifiSecurity.WPA -> R.string.qrscan_wifi_security_wpa
    WifiSecurity.WEP -> R.string.qrscan_wifi_security_wep
    WifiSecurity.OPEN -> R.string.qrscan_wifi_security_open
}

internal fun WifiCredentials.toPlainText(resources: Resources): String =
    rows(resources).toPlainText(resources.getString(R.string.qrscan_wifi_heading))
