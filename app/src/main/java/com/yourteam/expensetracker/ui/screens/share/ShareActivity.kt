package com.yourteam.expensetracker.ui.screens.share

import com.yourteam.expensetracker.BuildConfig
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.yourteam.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Receives a payment screenshot shared from GPay / PhonePe / any app ("Share screenshot").
 * The image stays on the phone and is not saved; only fields the user confirms will be kept.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), // icons follow system theme
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        logShapeOnly(imageUri, sharedText)

        setContent {
            ExpenseTrackerTheme {
                ConfirmPaymentScreen(
                    imageUri = imageUri,
                    hasSharedText = !sharedText.isNullOrBlank(),
                    onClose = ::finish,
                )
            }
        }
    }

    /** Debug only: what kind of share arrived. Never the image or text content. */
    private fun logShapeOnly(imageUri: Uri?, sharedText: String?) {
        if (!BuildConfig.TEST_BUILD) return
        Log.d(
            TAG,
            "action=${intent.action} type=${intent.type} from=${referrer?.host} " +
                "extras=${intent.extras?.keySet()?.sorted()} hasImage=${imageUri != null} " +
                "imageScheme=${imageUri?.scheme} textLength=${sharedText?.length ?: 0} " +
                "textHasRupee=${sharedText?.contains('₹') == true} clipItems=${intent.clipData?.itemCount ?: 0}",
        )
    }

    private companion object {
        const val TAG = "ShareIn"
    }
}
