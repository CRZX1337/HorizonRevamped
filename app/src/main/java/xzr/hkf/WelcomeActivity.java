package xzr.hkf;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class WelcomeActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final int MANAGE_STORAGE_PERMISSION_CODE = 101;
    private static final String PREFS_NAME = "HorizonPrefs";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    private ViewPager2 viewPager;
    private WelcomePagerAdapter pagerAdapter;
    private ExtendedFloatingActionButton nextButton;
    private LottieAnimationView lottieAnimationView;
    
    private final ActivityResultLauncher<Intent> storagePermissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), 
            result -> checkPermissionsAndProceed());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if this is first run
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (!settings.getBoolean(KEY_FIRST_RUN, true)) {
            // If not first run, go directly to main activity
            startMainActivity();
            return;
        }
        
        setContentView(R.layout.activity_welcome);
        
        // Initialize views
        viewPager = findViewById(R.id.welcome_viewpager);
        nextButton = findViewById(R.id.welcome_next_button);
        lottieAnimationView = findViewById(R.id.welcome_lottie_animation);
        
        // Set up ViewPager with adapter
        pagerAdapter = new WelcomePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        
        // Handle page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateButtonText(position);
                playPageAnimation(position);
            }
        });
        
        // Set up button click
        nextButton.setOnClickListener(v -> {
            int currentPosition = viewPager.getCurrentItem();
            if (currentPosition < pagerAdapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentPosition + 1);
            } else {
                checkPermissionsAndProceed();
            }
        });
        
        // Initial animations
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        nextButton.startAnimation(fadeIn);
        
        // Start with first page animation
        playPageAnimation(0);
    }
    
    private void updateButtonText(int position) {
        if (position == pagerAdapter.getItemCount() - 1) {
            nextButton.setText(R.string.get_started);
            nextButton.setIconResource(R.drawable.ic_check);
        } else {
            nextButton.setText(R.string.next);
            nextButton.setIconResource(R.drawable.ic_arrow_forward);
        }
        
        // Button animation on text change
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.button_pulse);
        nextButton.startAnimation(pulse);
    }
    
    private void playPageAnimation(int position) {
        // Different animation for each page
        switch (position) {
            case 0:
                lottieAnimationView.setAnimation(R.raw.welcome_animation);
                break;
            case 1:
                lottieAnimationView.setAnimation(R.raw.storage_permission);
                break;
            case 2:
                lottieAnimationView.setAnimation(R.raw.root_permission);
                break;
        }
        
        lottieAnimationView.playAnimation();
    }
    
    private void checkPermissionsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ uses the MANAGE_EXTERNAL_STORAGE permission
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    storagePermissionLauncher.launch(intent);
                    return;
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    storagePermissionLauncher.launch(intent);
                    return;
                }
            }
        } else {
            // For Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                        STORAGE_PERMISSION_CODE);
                return;
            }
        }
        
        // If we reach here, permissions are granted
        markFirstRunComplete();
        startMainActivity();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                markFirstRunComplete();
                startMainActivity();
            } else {
                Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void markFirstRunComplete() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(KEY_FIRST_RUN, false);
        editor.apply();
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }
} 