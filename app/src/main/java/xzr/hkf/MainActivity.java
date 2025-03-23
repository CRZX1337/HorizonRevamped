package xzr.hkf;

import androidx.appcompat.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialSharedAxis;

import android.view.Gravity;

import android.app.Activity;

public class MainActivity extends AppCompatActivity {
    static final boolean DEBUG = false;

    TextView logView;
    NestedScrollView scrollView;

    enum status {
        flashing,
        flashing_done,
        error,
        normal
    }

    static status cur_status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply splash screen
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        
        // Set up transitions with 3D effect
        getWindow().setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        getWindow().setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
        getWindow().setAllowEnterTransitionOverlap(false);
        getWindow().setAllowReturnTransitionOverlap(false);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        scrollView = findViewById(R.id.scrollView);
        logView = findViewById(R.id.logView);
        
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        
        // Set up FAB with enhanced 3D animation
        ExtendedFloatingActionButton fabFlash = findViewById(R.id.fab_flash);
        fabFlash.setVisibility(View.INVISIBLE); // Hide initially for animation
        fabFlash.postDelayed(() -> {
            fabFlash.setVisibility(View.VISIBLE);
            
            // Create combined 3D entrance animation
            fabFlash.setAlpha(0f);
            fabFlash.setScaleX(0.6f);
            fabFlash.setScaleY(0.6f);
            fabFlash.setTranslationY(100f);
            fabFlash.setRotationX(-20f); // Add 3D rotation
            
            fabFlash.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .rotationX(0f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        }, 500); // Delay for a smoother entry
        
        fabFlash.setOnClickListener(v -> {
            // Add enhanced 3D click animation
            v.animate()
                .setDuration(100)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .rotationX(10f) // Add 3D tilt on press
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    v.animate()
                        .setDuration(300)
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotationX(0f)
                        .setInterpolator(new OvershootInterpolator())
                        .start();
                    flash_new();
                })
                .start();
        });
        
        // Initialize status
        cur_status = status.normal;
        update_title();
        
        // Apply card animation with 3D effect
        MaterialCardView logCard = findViewById(R.id.log_card);
        logCard.setAlpha(0f);
        logCard.setTranslationY(100f);
        logCard.setRotationX(10f); // Add 3D perspective
        logCard.animate()
                .alpha(1f)
                .translationY(0f)
                .rotationX(0f)
                .setDuration(700)
                .setStartDelay(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    void update_title() {
        runOnUiThread(() -> {
            switch (cur_status) {
                case error:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.failed);
                    }
                    break;
                case flashing:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.flashing);
                    }
                    break;
                case flashing_done:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.flashing_done);
                    }
                    break;
                default:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.app_name);
                    }
            }
        });
    }

    void flash_new() {
        if (cur_status == MainActivity.status.flashing) {
            // Show animated toast instead of regular Toast
            Snackbar.make(findViewById(R.id.fab_flash), R.string.task_running, Snackbar.LENGTH_SHORT)
                    .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                    .show();
            return;
        }

        // Animate log clearing
        if (logView.getText().length() > 0) {
            logView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        logView.setText("");
                        logView.animate().alpha(1f).setDuration(200).start();
                    })
                    .start();
        } else {
            logView.setText("");
        }
        
        cur_status = status.normal;
        update_title();
        
        // Show animated message
        Snackbar.make(findViewById(R.id.fab_flash), R.string.please_select_kzip, Snackbar.LENGTH_LONG)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .show();
                
        runWithFilePath(this, new Worker(this));
    }

    @Override
    public void onBackPressed() {
        if (cur_status != status.flashing) {
            // Add enhanced exit animation with 3D effect
            View logCard = findViewById(R.id.log_card);
            View fabFlash = findViewById(R.id.fab_flash);
            
            logCard.animate()
                .alpha(0f)
                .translationY(50f)
                .rotationX(15f) // Add 3D rotation for exit
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator())
                .start();
                
            fabFlash.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationY(50f)
                .rotationX(15f) // Add 3D rotation for exit
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator())
                .start();
            
            // Delay the actual back action to allow animations to play
            new android.os.Handler().postDelayed(() -> super.onBackPressed(), 300);
        } else {
            // Show message that flashing is in progress
            Snackbar.make(findViewById(R.id.fab_flash), R.string.task_running, Snackbar.LENGTH_SHORT)
                    .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                    .show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.about) {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            
            Button btnMainDev = dialogView.findViewById(R.id.btn_main_dev);
            Button btnTelegram = dialogView.findViewById(R.id.btn_telegram);
            
            // Add null checks to prevent crashes
            
            if (btnMainDev != null) {
                btnMainDev.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CRZX1337")));
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            if (btnTelegram != null) {
                btnTelegram.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HorizonRevamped")));
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            dialog.show();
        } else if (item.getItemId() == R.id.flash_new) {
            flash_new();
        }
        return true;
    }

    public static void _appendLog(String log, Activity activity) {
        activity.runOnUiThread(() -> {
            TextView logView = ((MainActivity) activity).logView;
            NestedScrollView scrollView = ((MainActivity) activity).scrollView;
            
            // Animate new log entries
            int startPosition = logView.getText().length();
            logView.append(log + "\n");
            int endPosition = logView.getText().length();
            
            if (android.text.Spannable.class.isAssignableFrom(logView.getText().getClass())) {
                android.text.Spannable spannable = (android.text.Spannable) logView.getText();
                android.text.style.ForegroundColorSpan highlightSpan = new android.text.style.ForegroundColorSpan(
                        activity.getResources().getColor(R.color.md_theme_light_primary));
                spannable.setSpan(highlightSpan, startPosition, endPosition, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                // Remove highlight after a delay
                new android.os.Handler().postDelayed(() -> {
                    try {
                        spannable.removeSpan(highlightSpan);
                    } catch (Exception e) {
                        // Ignore if span was already removed
                    }
                }, 1500);
            }
            
            scrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    public static void appendLog(String log, Activity activity) {
        if (DEBUG) {
            _appendLog(log, activity);
            return;
        }
        if (!log.startsWith("ui_print"))
            return;
        log = log.replace("ui_print", "");
        _appendLog(log, activity);
    }

    static class fileWorker extends Thread {
        public Uri uri;
    }

    private static fileWorker file_worker;

    public static void runWithFilePath(Activity activity, fileWorker what) {
        MainActivity.file_worker = what;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            file_worker.uri = data.getData();
            if (file_worker != null) {
                file_worker.start();
                file_worker = null;
            }
        }
    }
}