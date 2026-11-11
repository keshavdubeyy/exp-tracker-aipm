package com.yourteam.expensetracker.ui.screens.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Heading
import com.yourteam.expensetracker.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Step 1: show the shared screenshot. Reading amount / payee from it comes next. */
@Composable
fun ConfirmPaymentScreen(imageUri: Uri?, hasSharedText: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(imageUri != null) }

    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            bitmap = loadScaled(context, imageUri, maxSide = 1600)
            loading = false
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Heading(text = "Confirm payment")

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        imageUri == null -> "No screenshot came with this share. Try GPay's Share screenshot button."
                        hasSharedText -> "Got the screenshot and a text summary. Reading the amount comes in the next step."
                        else -> "Got the screenshot. Reading the amount from it comes in the next step."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                when {
                    loading -> CircularProgressIndicator()
                    bmp != null -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Shared payment screenshot",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(CardShape),
                    )
                    imageUri != null -> Text("Couldn't open this image.")
                }
            }

            Button(onClick = onClose, shape = PillShape, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

/** Decodes the shared image downscaled, so huge screenshots don't run out of memory. */
private suspend fun loadScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
