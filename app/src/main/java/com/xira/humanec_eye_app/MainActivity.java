package com.xira.humanec_eye_app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import com.xira.humanec_eye_app.utils.DateTimeValidator;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import com.xira.humanec_eye_app.databinding.ActivityMainBinding;
import com.xira.humanec_eye_app.ui.auth.LoginActivity;
import com.xira.humanec_eye_app.ui.camera.RecognizeActivity;
import com.xira.humanec_eye_app.ui.camera.autoSync.SyncScheduler;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        // ⭐ Foreground sync ke liye notification channel
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    "sync_channel",
//                    "Sync Service",
//                    NotificationManager.IMPORTANCE_LOW
//            );
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            if (manager != null) {
//                manager.createNotificationChannel(channel);
//            }
//        }


        SyncScheduler.scheduleSync(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        final boolean isNotHome = getIntent().getBooleanExtra("home", false);

        if (!isUserLoggedIn()) {
            redirectToLogin();
            return;
        }
        if (!DateTimeValidator.validateAndShowDialog(this)) {
            return;
        }
        
        if (!isNotHome) {
            redirectToCamera();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(binding.navView, navController);

        binding.navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_register) {
                navController.popBackStack(R.id.navigation_register, true);
                navController.navigate(R.id.navigation_register);
                return true;
            } else if (itemId == R.id.navigation_setting) {
                navController.popBackStack(R.id.navigation_setting, true);
                navController.navigate(R.id.navigation_setting);
                return true;
            }
            return false;
        });

        binding.fabCamera.setOnClickListener(v -> {
            redirectToCamera();
        });

        View rootView = binding.container;

        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomPadding = insets.getSystemWindowInsetBottom(); // Get navigation bar height
            v.setPadding(0, 0, 0, bottomPadding); // Apply padding
            return insets;
        });
    }

    private boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (prefs == null) {
            Log.e("MainActivity", "SharedPreferences is NULL");
            return false;
        }

        String accessToken = prefs.getString("access_token", null);
        Log.d("MainActivity", "Access Token: " + accessToken);
        return accessToken != null;
    }

    private void redirectToLogin() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 500);
    }

    private void redirectToCamera() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, RecognizeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 400);
    }
}