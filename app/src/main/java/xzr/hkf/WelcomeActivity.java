package xzr.hkf;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.DataOutputStream;
import java.io.IOException;

public class WelcomeActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final int MANAGE_STORAGE_PERMISSION_CODE = 101;
    private static final String PREFS_NAME = "HorizonPrefs";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    private ViewPager2 viewPager;
    private WelcomePagerAdapter pagerAdapter;
    private ExtendedFloatingActionButton nextButton;
    private LottieAnimationView lottieAnimationView;
    private LinearProgressIndicator progressIndicator;
    
    private final ActivityResultLauncher<Intent> storagePermissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), 
            result -> {
                // After returning from the permission screen, check if permission was granted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        // Storage permission granted, now check for root access
                        checkRootAccessAndProceed();
                    } else {
                        // Permission denied - inform the user
                        Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
                    }
                } else {
                    // For older Android versions, check the regular permission
                    checkPermissionsAndProceed();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
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
            progressIndicator = findViewById(R.id.welcome_progress);
            
            // Initialize progress indicator
            progressIndicator.setMax(2);
            progressIndicator.setProgress(0);
            
            // Set up ViewPager with adapter
            pagerAdapter = new WelcomePagerAdapter(this);
            viewPager.setAdapter(pagerAdapter);
            
            // Handle page changes
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    try {
                        updateButtonText(position);
                        playPageAnimation(position);
                        progressIndicator.setProgress(position);
                    } catch (Exception e) {
                        // Ignore animation errors to prevent crashes
                    }
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
            try {
                Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                nextButton.startAnimation(fadeIn);
            } catch (Exception e) {
                // Fallback if animation fails
                nextButton.setAlpha(0f);
                nextButton.setVisibility(View.VISIBLE);
                nextButton.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start();
            }
            
            // Start with first page animation - in a try-catch to prevent startup crashes
            try {
                playPageAnimation(0);
            } catch (Exception e) {
                // If the initial animation fails, don't let the app crash
                if (lottieAnimationView != null) {
                    lottieAnimationView.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            // Last resort fallback - if we hit a fatal error during setup, 
            // skip welcome and go to main activity
            try {
                startMainActivity();
            } catch (Exception ex) {
                // If even that fails, at least don't crash the app
                finish();
            }
        }
    }
    
    private void updateButtonText(int position) {
        if (position == pagerAdapter.getItemCount() - 1) {
            nextButton.setText(R.string.get_started);
            nextButton.setIconResource(R.drawable.ic_check);
        } else {
            nextButton.setText(R.string.next);
            nextButton.setIconResource(R.drawable.ic_arrow_forward);
        }
        
        // Use property animation directly instead of loading from XML
        nextButton.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(150)
            .withEndAction(() -> 
                nextButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .start()
            )
            .start();
    }
    
    private void playPageAnimation(int position) {
        try {
            // DISABLED ANIMATIONS TEMPORARILY FOR DEBUGGING
            // Different animation for each page
            /*
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
            */
            
            // Just show a placeholder without animation
            lottieAnimationView.cancelAnimation();
            lottieAnimationView.clearAnimation();
            lottieAnimationView.setImageDrawable(null);
            lottieAnimationView.setVisibility(View.VISIBLE);
            
            // Simple alpha animation as fallback
            lottieAnimationView.setAlpha(0f);
            lottieAnimationView.animate()
                .alpha(1f)
                .setDuration(500)
                .start();
        } catch (Exception e) {
            // If Lottie animation fails, use a simple fade-in animation as fallback
            lottieAnimationView.cancelAnimation();
            lottieAnimationView.clearAnimation();
            
            // Show image without animation if possible
            lottieAnimationView.setImageDrawable(null);
            lottieAnimationView.setVisibility(View.VISIBLE);
            
            // Simple alpha animation as fallback
            lottieAnimationView.setAlpha(0f);
            lottieAnimationView.animate()
                .alpha(1f)
                .setDuration(500)
                .start();
        }
    }
    
    private void checkPermissionsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ uses the MANAGE_EXTERNAL_STORAGE permission
            if (!Environment.isExternalStorageManager()) {
                try {
                    // Use explicit intent to ensure we reach the right screen
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    
                    // Check if the intent can be resolved
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        storagePermissionLauncher.launch(intent);
                    } else {
                        // Fallback to the general permission screen
                        intent = new Intent();
                        intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        storagePermissionLauncher.launch(intent);
                    }
                    return;
                } catch (Exception e) {
                    // If there's an exception, show an error message and try to continue
                    Toast.makeText(this, "Failed to request storage permissions. Please grant permissions manually.", Toast.LENGTH_LONG).show();
                    // Try the old way
                    requestLegacyStoragePermission();
                    return;
                }
            }
        } else {
            // For Android 10 and below - use the legacy permission system
            requestLegacyStoragePermission();
            return;
        }
        
        // If we reach here, storage permissions are granted
        // Now check for root access
        checkRootAccessAndProceed();
    }
    
    private void checkRootAccessAndProceed() {
        // Check for root access in a background thread
        new Thread(() -> {
            boolean hasRootAccess = checkRootAccess();
            runOnUiThread(() -> {
                if (hasRootAccess) {
                    // Root access granted
                    markFirstRunComplete();
                    startMainActivity();
                } else {
                    // No root access, show dialog
                    showRootAccessRequiredDialog();
                }
            });
        }).start();
    }
    
    private boolean checkRootAccess() {
        boolean rootAccess = false;
        Process process = null;
        try {
            // Try to execute a simple command with SU
            process = Runtime.getRuntime().exec("su -c id");
            
            // Wait for the command to complete
            int exitValue = process.waitFor();
            rootAccess = (exitValue == 0);
        } catch (Exception e) {
            // Any exception means no root access
            rootAccess = false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return rootAccess;
    }
    
    private void showRootAccessRequiredDialog() {
        // Create a dialog with custom view
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_root_required);
        dialog.setCancelable(false);
        
        // Set dialog window properties for 3D effect
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
            
            // Set window animations
            window.setWindowAnimations(R.style.DialogAnimation);
        }
        
        // Get references to views
        View dialogView = dialog.findViewById(android.R.id.content).getRootView();
        Button btnExit = dialog.findViewById(R.id.btn_exit);
        Button btnTryAgain = dialog.findViewById(R.id.btn_try_again);
        ImageView iconView = dialog.findViewById(R.id.dialog_icon);
        
        // Add a subtle pulsing effect to the icon
        if (iconView != null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.2f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.2f, 1f);
            
            // Set repeat count on individual animators, not on the AnimatorSet
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(scaleX, scaleY);
            pulse.setDuration(2000);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.start();
        }
        
        // Apply initial pop-up animation directly
        dialogView.setAlpha(0f);
        dialogView.setScaleX(0f);
        dialogView.setScaleY(0f);
        
        dialogView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(new android.view.animation.OvershootInterpolator())
            .withEndAction(() -> {
                // Apply floating animation after pop completes
                animateFloating(dialogView);
            })
            .start();
        
        // Apply simple button style
        btnExit.setOnClickListener(v -> {
            // Play fade out animation before closing
            dialogView.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationY(50f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    dialog.dismiss();
                    finish();
                })
                .start();
        });
        
        btnTryAgain.setOnClickListener(v -> {
            // Play fade out animation before retrying
            dialogView.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationY(50f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    dialog.dismiss();
                    checkRootAccessAndProceed();
                })
                .start();
        });
        
        dialog.show();
    }
    
    private void requestLegacyStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 
                    STORAGE_PERMISSION_CODE);
        } else {
            // Permission already granted
            markFirstRunComplete();
            startMainActivity();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Storage permissions granted, now check for root access
                checkRootAccessAndProceed();
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
    
    private void animateFloating(View view) {
        if (view == null) return;
        
        // Create a gentle floating animation
        view.animate()
            .translationY(-12f)
            .setDuration(1500)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .withEndAction(() -> {
                // Animate back to original position
                view.animate()
                    .translationY(0f)
                    .setDuration(1500)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        // Repeat the animation for a floating effect
                        animateFloating(view);
                    })
                    .start();
            })
            .start();
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }
} 