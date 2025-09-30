package com.example.notificationnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.notificationnotes.ui.theme.NotificationNotesTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

// Dialog Launcher
@Composable
fun AppLaunch(activity: ComponentActivity, intent: Intent) {
    // Opened state
    var isDialogOpen by rememberSaveable { mutableStateOf(value = true) }

    // Cast the activity to MainActivity to access showNotification
    val mainActivity = activity as MainActivity

    // If open, call dialog screen
    if (isDialogOpen) {
        NoteScreen(
            // On dismiss, close dialog and finish activity
            onDismiss = {
                isDialogOpen = false
                activity.finishAndRemoveTask()
            },
            // On save, close dialog and finish activity while saving content
            onSave = { title, content ->

                // Generate Unique ID
                var uniqueId = System.currentTimeMillis().toInt()

                // Check if initiated from notification
                if (intent.hasExtra("extra_id")) {
                    uniqueId = intent.getIntExtra("extra_id", 0)
                }


                // Posts Notification
                mainActivity.showNotification(id = uniqueId,  title = title, content = content)

                isDialogOpen = false
                activity.finishAndRemoveTask()
            },
            intent = intent
        )
    }
}

// Dialog Screen
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    // Dialog actions
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit,
    intent: Intent
    ) {
        // Initial content
        var noteTitleStart by rememberSaveable { mutableStateOf(value = "") }
        var noteContentStart by rememberSaveable { mutableStateOf(value = "") }

        // If initiated from notification, set initial content to notification data
        if (intent.hasExtra("extra_id")) {
            noteTitleStart = intent.getStringExtra("extra_title")?: ""
            noteContentStart = intent.getStringExtra("extra_content")?: ""
        }

        // Content variables
        var noteTitle by rememberSaveable { mutableStateOf(value = noteTitleStart) }
        var noteContent by rememberSaveable { mutableStateOf(value = noteContentStart) }

        // Window Name Logic
        var windowName by rememberSaveable { mutableStateOf(value = "") }
        windowName = if (intent.hasExtra("extra_id")) {
            "Edit Note"
        } else {
            "New Note"
        }

        // Error variables
        var titleNeeded by rememberSaveable { mutableStateOf(value = true) }

        // Error handling
        titleNeeded = noteTitle.isBlank() && noteContent.isNotBlank()

    // Dialog
        AlertDialog(
            // Dismiss when requested
            onDismissRequest = onDismiss,

            // Content
            title = {Text(text = windowName)},
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Title TextField
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text(text = "Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = titleNeeded,
                        supportingText = {
                            if (titleNeeded) {
                                Text(text = "Title is required")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(height = 8.dp))

                    // Content TextField
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text(text ="Content") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },

            // Buttons
            confirmButton = {
                Button(
                    onClick = {
                        onSave(noteTitle, noteContent)
                        onDismiss()
                    },
                    enabled = noteTitle.isNotBlank(),
                ) {
                    Text(text = "DONE")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text(text = "CANCEL")
                }
            }
        )
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawableResource(android.R.color.transparent)

        super.onCreate(savedInstanceState)

        createNotificationChannel()

        enableEdgeToEdge()
        setContent {
            NotificationNotesTheme {
                AppLaunch(activity = this, intent = intent)
            }
        }
    }

    // Notifications

    // Channel
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Create Channel
            val name = "Notification Notes"
            val descriptionText = "Displays notes as notifications. Required for this app to function."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("notification_notes", name, importance).apply {
                description = descriptionText
            }

            // Register Channel
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Notification Creator
    fun showNotification(id: Int, title: String, content: String) {

        // Create Intent (Data given when tapped)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("extra_id", id)
            putExtra("extra_title", title)
            putExtra("extra_content", content)

            // Make sure to not stack incorrectly
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        }

        // Create Pending Intent
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build Notification
        val builder = NotificationCompat.Builder(this, "notification_notes")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)

        // Get Notification Manager and post notification
        with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
            notify(id, builder.build())
        }
    }
}

// When quick tile is pressed, launch app
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class NoteTileService : TileService() {
    override fun onClick() {
        super.onClick()

        // Create Launch Intent
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Create Pending Intent of Launch Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Start activity
        startActivityAndCollapse(pendingIntent)
    }
}