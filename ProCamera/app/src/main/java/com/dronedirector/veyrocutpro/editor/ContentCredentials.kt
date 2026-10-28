package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * Content credentials (C2PA): exported photos and videos carry a signed record of who made them and that
 * they were edited in AlcorCut Pro — readable with the free Content Credentials checkers. Signed with a
 * certificate made on this phone for you (checkers show it as "unknown signer", since no public
 * authority vouches for it). Uses the open-source c2pa library (Apache 2.0 / MIT). Android 9 and newer.
 */
object ContentCredentials {
    private fun prefs(c: Context) = c.getSharedPreferences("content_credentials", Context.MODE_PRIVATE)
    fun on(c: Context, video: Boolean) = Build.VERSION.SDK_INT >= 28 && prefs(c).getBoolean(if (video) "video" else "photo", false)
    fun setOn(c: Context, video: Boolean, v: Boolean) = prefs(c).edit().putBoolean(if (video) "video" else "photo", v).apply()
    fun creator(c: Context) = prefs(c).getString("creator", "") ?: ""
    fun setCreator(c: Context, v: String) = prefs(c).edit().putString("creator", v.take(80)).apply()
    /** The last result (shown under the setting). */
    var last by mutableStateOf<String?>(null)

    private fun dir(c: Context) = File(c.filesDir, "c2pa").apply { mkdirs() }

    private fun pem(type: String, der: ByteArray) =
        "-----BEGIN $type-----\n" + android.util.Base64.encodeToString(der, android.util.Base64.NO_WRAP).chunked(64).joinToString("\n") + "\n-----END $type-----\n"

    /** This phone's signing key and certificate chain (made once: a small local authority and your certificate). */
    @Synchronized
    private fun keys(c: Context): Pair<String, String> {
        val kf = File(dir(c), "key.pem"); val cf = File(dir(c), "chain.pem")
        if (kf.exists() && cf.exists()) return kf.readText() to cf.readText()
        val name = creator(c).ifBlank { "AlcorCut Pro user" }.replace(Regex("[,=+<>#;\"\\\\]"), " ")
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val caKey = gen.generateKeyPair(); val key = gen.generateKeyPair()
        val now = Date(System.currentTimeMillis() - 86_400_000L); val end = Date(System.currentTimeMillis() + 10L * 365 * 86_400_000L)
        val caName = org.bouncycastle.asn1.x500.X500Name("CN=AlcorCut Pro local authority ($name), O=AlcorCut Pro")
        val ext = org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils()
        val caB = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(caName, BigInteger.valueOf(System.nanoTime()), now, end, caName, caKey.public)
            .addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, org.bouncycastle.asn1.x509.BasicConstraints(0))
            .addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true, org.bouncycastle.asn1.x509.KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.keyCertSign or org.bouncycastle.asn1.x509.KeyUsage.cRLSign))
            .addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(caKey.public))
        val signer = { k: java.security.PrivateKey -> org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA").build(k) }
        val caCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(caB.build(signer(caKey.private)))
        val subj = org.bouncycastle.asn1.x500.X500Name("CN=$name, O=AlcorCut Pro")
        val eeB = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(caCert, BigInteger.valueOf(System.nanoTime() + 1), now, end, subj, key.public)
            .addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, org.bouncycastle.asn1.x509.BasicConstraints(false))
            .addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true, org.bouncycastle.asn1.x509.KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature))
            .addExtension(org.bouncycastle.asn1.x509.Extension.extendedKeyUsage, false, org.bouncycastle.asn1.x509.ExtendedKeyUsage(org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_emailProtection))
            .addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(key.public))
            .addExtension(org.bouncycastle.asn1.x509.Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(caCert))
        val ee = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(eeB.build(signer(caKey.private)))
        val keyPem = pem("PRIVATE KEY", key.private.encoded)
        val chain = pem("CERTIFICATE", ee.encoded) + pem("CERTIFICATE", caCert.encoded)
        kf.writeText(keyPem); cf.writeText(chain)
        return keyPem to chain
    }

    /** Makes a new certificate next time (e.g. after changing the name). */
    fun resetKeys(c: Context) { File(dir(c), "key.pem").delete(); File(dir(c), "chain.pem").delete() }

    private fun manifest(c: Context, title: String, format: String, video: Boolean): String {
        val ver = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }
        val who = creator(c).replace("\\", "\\\\").replace("\"", "\\\"")
        val t = title.replace("\\", "\\\\").replace("\"", "\\\"")
        val author = if (who.isBlank()) "" else ""","author":[{"@type":"Person","name":"$who"}]"""
        return """{"claim_generator_info":[{"name":"AlcorCut Pro","version":"$ver"}],"title":"$t","format":"$format",
"assertions":[
{"label":"c2pa.actions","data":{"actions":[
{"action":"c2pa.created","digitalSourceType":"http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture","softwareAgent":{"name":"AlcorCut Pro","version":"$ver"}},
{"action":"c2pa.edited","softwareAgent":{"name":"AlcorCut Pro","version":"$ver"},"description":"${if (video) "Edited in the AlcorCut Pro video editor" else "Developed and edited in the AlcorCut Pro photo editor"}"}]}},
{"label":"stds.schema-org.CreativeWork","data":{"@context":"https://schema.org","@type":"CreativeWork"$author}}
]}"""
    }

    /**
     * Adds content credentials to the finished file [out] (a gallery file). Returns null when done, else why not.
     * Runs off the main thread.
     */
    fun sign(c: Context, out: Uri, video: Boolean): String? {
        if (Build.VERSION.SDK_INT < 28) return "Content credentials need Android 9 or newer"
        val mime = c.contentResolver.getType(out) ?: if (video) "video/mp4" else "image/jpeg"
        val ext = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/heif", "image/heic" -> "heic"; "image/avif" -> "avif"; "image/tiff" -> "tiff"; "video/quicktime" -> "mov"; else -> if (video) "mp4" else "jpg" }
        val name = try { c.contentResolver.query(out, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } } catch (_: Throwable) { null } ?: "export.$ext"
        val src = File(c.cacheDir, "c2pa_in.$ext"); val dst = File(c.cacheDir, "c2pa_out.$ext")
        return try {
            c.contentResolver.openInputStream(out)?.use { ins -> src.outputStream().use { ins.copyTo(it, 1 shl 20) } } ?: return "Couldn't read the file"
            dst.delete()
            val (key, chain) = keys(c)
            org.contentauth.c2pa.C2PA.signFile(src.path, dst.path, manifest(c, name, mime, video),
                org.contentauth.c2pa.SignerInfo(org.contentauth.c2pa.SigningAlgorithm.ES256, chain, key, null))
            c.contentResolver.openOutputStream(out, "wt")?.use { os -> dst.inputStream().use { it.copyTo(os, 1 shl 20) } } ?: return "Couldn't write the file"
            com.dronedirector.veyrocutpro.ui.AppLog.i("Content credentials added to $name")
            last = "Content credentials added to $name"
            null
        } catch (t: Throwable) {
            com.dronedirector.veyrocutpro.ui.AppLog.error("Content credentials failed for $name", t, false)
            last = "Couldn't add content credentials: ${t.message?.take(120)}"
            last
        } finally { src.delete(); dst.delete() }
    }

    /** Self-test (emulator): sign a small picture and read the record back. */
    fun selfTest(c: Context): String = try {
        if (Build.VERSION.SDK_INT < 28) "skipped (Android < 9)" else {
            val f = File(c.cacheDir, "c2pa_test.jpg"); val o = File(c.cacheDir, "c2pa_test_signed.jpg")
            val b = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366AA.toInt()) }
            f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }; b.recycle(); o.delete()
            val (key, chain) = keys(c)
            org.contentauth.c2pa.C2PA.signFile(f.path, o.path, manifest(c, "test.jpg", "image/jpeg", false), org.contentauth.c2pa.SignerInfo(org.contentauth.c2pa.SigningAlgorithm.ES256, chain, key, null))
            val back = org.contentauth.c2pa.C2PA.readFile(o.path)
            f.delete(); o.delete()
            "ok, ${back.length} chars, has AlcorCut: ${back.contains("AlcorCut Pro")}"
        }
    } catch (t: Throwable) { "FAILED: ${t.javaClass.simpleName} ${t.message}" }
}

/** The setting (photo Export / video Deliver): on or off, and your name in the record. */
@Composable
fun ContentCredentialsSection(video: Boolean) {
    val c = LocalContext.current
    var on by remember { mutableStateOf(ContentCredentials.on(c, video)) }
    var who by remember { mutableStateOf(ContentCredentials.creator(c)) }
    SectionTitle("CONTENT CREDENTIALS")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Add content credentials", color = Color.White, fontSize = 14.sp)
            Text("A signed record in each exported ${if (video) "video" else "photo"}: who made it and that it was edited in AlcorCut Pro (the open C2PA standard — shown by Content Credentials checkers and some websites). Signed with a certificate made on this phone, so checkers show the signer as not verified by an authority.", color = Pro.Dim, fontSize = 11.sp)
        }
        Switch(on, { v -> if (Build.VERSION.SDK_INT >= 28) { on = v; ContentCredentials.setOn(c, video, v) } }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber))
    }
    if (Build.VERSION.SDK_INT < 28) Text("Needs Android 9 or newer.", color = Pro.Dim, fontSize = 11.sp)
    if (on) OutlinedTextField(who, { v -> who = v; ContentCredentials.setCreator(c, v); ContentCredentials.resetKeys(c) }, label = { Text("Your name (as the creator)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
    ContentCredentials.last?.let { Text(it, color = Pro.Amber, fontSize = 11.sp) }
}
