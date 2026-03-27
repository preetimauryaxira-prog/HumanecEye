import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.xira.humanec_eye_app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarkAttendanceDialog(context: Context, private val empName: String) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the dialog to be full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.mark_attendance_dialog)

        // Set the window attributes to make the dialog full screen
        window?.let {
            // Make the background transparent
            it.setBackgroundDrawableResource(android.R.color.transparent)

            // Fill the screen
            it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

            // Set status bar and navigation bar color to match the dialog background
            it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.statusBarColor = context.resources.getColor(R.color.white_50_percent_transparent) // Replace with your color
            it.navigationBarColor = context.resources.getColor(R.color.white_50_percent_transparent) // Replace with your color
        }

        // Play success sound
        playSuccessSound(context)

        // Set the attendance details
        val attendanceName = findViewById<TextView>(R.id.attendanceName)
        val attendanceTime = findViewById<TextView>(R.id.attendanceTime)

        attendanceName.text = "Attendance Marked for $empName"
        attendanceTime.text = "${getCurrentTime()}"

        // Play the checkmark animation
        val checkmarkAnimation = findViewById<LottieAnimationView>(R.id.checkmarkAnimation)
        checkmarkAnimation.playAnimation()

        // Dismiss the dialog after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, 2000)
    }

    private fun playSuccessSound(context: Context) {
        val mediaPlayer = MediaPlayer.create(context, R.raw.thank_you)
        if (mediaPlayer != null) {
            mediaPlayer.start()

            // Stop sound after 2 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                    mediaPlayer.release()
                }
            }, 2000)
        } else {
            Log.e("MediaPlayer", "Failed to create MediaPlayer")
        }
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return dateFormat.format(Date())
    }
}