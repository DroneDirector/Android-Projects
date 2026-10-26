package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Help → Proof of purchase: the AlcorCut Pro purchase on this Google account, as Google Play
 * reports it (product, order number, date, state), with links to Play. Own code; Google Play
 * Billing Library.
 */
object ProofOfPurchase {
    /** The in-app product that unlocks AlcorCut Pro (set up in the Play Console with this id). */
    const val PRODUCT = "alcorcut_pro"

    fun installer(c: Context): String = try {
        val src = if (android.os.Build.VERSION.SDK_INT >= 30) c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName
        else @Suppress("DEPRECATION") c.packageManager.getInstallerPackageName(c.packageName)
        when (src) { "com.android.vending" -> "Google Play"; null -> "Installed by hand (APK)"; else -> src }
    } catch (_: Throwable) { "Unknown" }
}

@Composable
fun ProofOfPurchaseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf("Asking Google Play…") }
    var purchases by remember { mutableStateOf<List<Purchase>?>(null) }
    DisposableEffect(Unit) {
        val client = BillingClient.newBuilder(context)
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                        state = "Google Play isn't available here (" + r.debugMessage.ifBlank { "code ${r.responseCode}" } + ")."
                        return
                    }
                    client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { qr, list ->
                        if (qr.responseCode == BillingClient.BillingResponseCode.OK) { purchases = list; state = "" }
                        else state = "Google Play couldn't list the purchases (" + qr.debugMessage.ifBlank { "code ${qr.responseCode}" } + ")."
                    }
                }
                override fun onBillingServiceDisconnected() {}
            })
        } catch (t: Throwable) { state = "Google Play isn't available here." }
        onDispose { try { client.endConnection() } catch (_: Throwable) {} }
    }
    fun open(u: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {} }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Proof of purchase", color = Color.White) },
        text = {
            Column {
                @Composable
                fun RowT(k: String, v: String) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(k, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                        Text(v, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
                RowT("App", "AlcorCut Pro " + (try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { "" }))
                RowT("Installed from", ProofOfPurchase.installer(context))
                if (state.isNotEmpty()) Text(state, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                val mine = purchases?.filter { ProofOfPurchase.PRODUCT in it.products }.orEmpty()
                if (purchases != null && mine.isEmpty()) Text("No AlcorCut Pro purchase on the Google account that's signed in to Play on this phone. Bought it on another account? Switch account in the Play Store.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                for (p in mine) {
                    Text("PURCHASE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp))
                    RowT("Product", "AlcorCut Pro (" + p.products.joinToString() + ")")
                    RowT("Order number", p.orderId ?: "—")
                    RowT("Bought", java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.purchaseTime)))
                    RowT("State", when (p.purchaseState) { Purchase.PurchaseState.PURCHASED -> "Paid — valid"; Purchase.PurchaseState.PENDING -> "Payment pending"; else -> "Unknown" })
                    RowT("Confirmed", if (p.isAcknowledged) "Yes" else "Not yet")
                }
                Text("The purchase is kept by Google Play on your Google account — the app only reads it.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { open("https://play.google.com/store/account/orderhistory") }) { Text("Order history", color = Pro.Amber) }
                TextButton(onClick = { open("https://play.google.com/store/apps/details?id=" + context.packageName) }) { Text("Play Store", color = Pro.Amber) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White) } },
    )
}
