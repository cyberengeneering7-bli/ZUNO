/**
 * ZUNO - login ke baad token backup popup
 * Based on Meld / Metrolist (GPL-3.0)
 */

package com.metrolist.music.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.TokenBackupPendingKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.utils.rememberPreference

/**
 * Fresh login ke baad ek baar dikhta hai.
 * Token sirf phone mein rehta hai, kisi server pe nahi bheja jata.
 * Same format "Advanced login" (Account > token) mein paste hota hai.
 */
@Composable
fun TokenBackupPrompt() {
    val context = LocalContext.current

    val (pending, setPending) = rememberPreference(TokenBackupPendingKey, false)
    val (cookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, _) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, _) = rememberPreference(DataSyncIdKey, "")
    val (authUser, _) = rememberPreference(InnerTubeAuthUserKey, "0")
    val (accountName, _) = rememberPreference(AccountNameKey, "")
    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, _) = rememberPreference(AccountChannelHandleKey, "")

    var showToken by remember { mutableStateOf(false) }

    if (!pending || cookie.isBlank()) return

    val tokenText =
        """
        ***INNERTUBE COOKIE*** =$cookie
        ***VISITOR DATA*** =$visitorData
        ***DATASYNC ID*** =$dataSyncId
        ***AUTH USER*** =$authUser
        ***ACCOUNT NAME*** =$accountName
        ***ACCOUNT EMAIL*** =$accountEmail
        ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
        """.trimIndent()

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Save your token") },
        text = {
            Column {
                Text(
                    text =
                        "This token lets you sign in to ZUNO on another phone and get your " +
                            "likes and library back. Keep it private: anyone who has it can " +
                            "use your account.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Hide token" else "Show token")
                }
                if (showToken) {
                    Column(
                        modifier =
                            Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = tokenText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ZUNO token", tokenText))
                    Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Copy token")
            }
        },
        dismissButton = {
            TextButton(onClick = { setPending(false) }) {
                Text("Done")
            }
        },
    )
}
