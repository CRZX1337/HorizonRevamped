package xzr.hkf;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import xzr.hkf.utils.AssetsUtil;

public class Worker extends MainActivity.fileWorker {
    Activity activity;
    String file_path;
    String binary_path;
    boolean is_error;

    public Worker(Activity activity) {
        this.activity = activity;
    }

    private androidx.appcompat.app.AlertDialog progressDialog;
    private TextView progressText;
    private long startTime;
    
    // Method to get formatted timestamp
    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    // Enhanced logging method with timestamps
    private void logWithTimestamp(String message) {
        String timestampedMessage = activity.getString(R.string.log_timestamp, getTimestamp(), message);
        MainActivity._appendLog(timestampedMessage, activity);
    }
    
    // Method to log elapsed time
    private String getElapsedTime() {
        long elapsedMillis = System.currentTimeMillis() - startTime;
        long seconds = elapsedMillis / 1000;
        long millis = elapsedMillis % 1000;
        return String.format(Locale.getDefault(), "%d.%03ds", seconds, millis);
    }
    
    public void run() {
        // Record start time
        startTime = System.currentTimeMillis();
        MainActivity.cur_status = MainActivity.status.flashing;
        ((MainActivity) activity).update_title();
        is_error = false;
        
        // Log start of flashing process
        logWithTimestamp("Starting flashing process");
        logWithTimestamp("Initializing environment");
        
        file_path = activity.getFilesDir().getAbsolutePath() + "/" + DocumentFile.fromSingleUri(activity, uri).getName();
        binary_path = activity.getFilesDir().getAbsolutePath() + "/META-INF/com/google/android/update-binary";
        
        // Log file information
        logWithTimestamp("Target file: " + DocumentFile.fromSingleUri(activity, uri).getName());
        
        // Show flashing progress dialog
        activity.runOnUiThread(() -> {
            View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_flashing_progress, null);
            progressText = dialogView.findViewById(R.id.progress_text);
            progressDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
            updateProgress("Preparing environment...");
        });

        try {
            cleanup();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_cleanup), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            return;
        }
        updateProgress("Checking root access...");

        if (!rootAvailable()) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_root), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_flash_root, false);
            return;
        }

        updateProgress("Copying files...");
        try {
            copy();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (!is_error)
            is_error = !new File(file_path).exists();
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_copy), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_copy, false);
            return;
        }

        updateProgress("Extracting binary...");
        try {
            getBinary();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_get_exe), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_get_exe, false);
            return;
        }

        updateProgress("Patching binary...");
        try {
            patch();
        } catch (IOException ignored) {
        }

        updateProgress("Flashing kernel...");
        showStatusNotification(R.string.flashing, true);
        try {
            flash(activity);
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_error), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_flash_error, false);
            return;
        }
        dismissProgressDialog();
        showStatusNotification(R.string.flashing_done, true);
        
        // Log completion time
        logWithTimestamp("Flashing completed successfully in " + getElapsedTime());
        
        // Show animated success screen
        activity.runOnUiThread(() -> {
            View successView = activity.getLayoutInflater().inflate(R.layout.dialog_success_screen, null);
            
            // Get views
            MaterialCardView successCard = successView.findViewById(R.id.success_card);
            ImageView successIcon = successView.findViewById(R.id.success_icon);
            Button btnReboot = successView.findViewById(R.id.btn_reboot);
            Button btnCancel = successView.findViewById(R.id.btn_cancel);
            TextView successTitle = successView.findViewById(R.id.success_title);
            TextView successMessage = successView.findViewById(R.id.success_message);
            
            // Set custom success icon
            successIcon.setImageResource(R.drawable.ic_success_checkmark);
            successIcon.setColorFilter(activity.getResources().getColor(R.color.md_theme_light_primary));
            
            // Set completion time in message
            successMessage.setText(activity.getString(R.string.reboot_complete_msg) + 
                    "\n\nCompleted in " + getElapsedTime());
            
            // Create and show dialog
            androidx.appcompat.app.AlertDialog successDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setView(successView)
                    .setCancelable(false)
                    .create();
            
            // Set button actions
            btnReboot.setOnClickListener(v -> {
                try {
                    successDialog.dismiss();
                    reboot();
                } catch (IOException e) {
                    Toast.makeText(activity, R.string.failed_reboot, Toast.LENGTH_SHORT).show();
                }
            });
            
            btnCancel.setOnClickListener(v -> successDialog.dismiss());
            
            // Show dialog
            successDialog.show();
            
            // Apply animations
            successCard.setAlpha(0f);
            successCard.setScaleX(0.8f);
            successCard.setScaleY(0.8f);
            
            successCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .start();
            
            // Apply animation to success icon
            successIcon.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                    activity, R.anim.checkmark_animation));
        });
        MainActivity.cur_status = MainActivity.status.flashing_done;
        ((MainActivity) activity).update_title();
    }

    boolean rootAvailable() {
        try {
            String ret = runWithNewProcessReturn(true, "id");
            return ret != null && ret.contains("root");
        } catch (IOException e) {
            return false;
        }
    }

    void copy() throws IOException {
        logWithTimestamp("Copying file to working directory");
        InputStream inputStream = activity.getContentResolver().openInputStream(uri);
        FileOutputStream fileOutputStream = new FileOutputStream(new File(file_path));
        byte[] buffer = new byte[1024];
        int count = 0;
        long totalBytes = 0;
        
        while ((count = inputStream.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, count);
            totalBytes += count;
        }
        
        fileOutputStream.flush();
        inputStream.close();
        fileOutputStream.close();
        
        // Log file size
        float fileSizeKB = totalBytes / 1024f;
        if (fileSizeKB > 1024) {
            logWithTimestamp(String.format(Locale.getDefault(), "File copied successfully (%.2f MB)", fileSizeKB / 1024));
        } else {
            logWithTimestamp(String.format(Locale.getDefault(), "File copied successfully (%.2f KB)", fileSizeKB));
        }
    }

    void getBinary() throws IOException {
        logWithTimestamp("Creating directory structure for binary");
        runWithNewProcessNoReturn(true, "mkdir -p " + activity.getFilesDir().getAbsolutePath() + "/META-INF/com/google/android");
        
        logWithTimestamp("Extracting update binary from zip");
        String extractResult = runWithNewProcessReturn(true, "unzip -p " + file_path + " META-INF/com/google/android/update-binary > " + binary_path);
        if (extractResult != null && !extractResult.isEmpty()) {
            logWithTimestamp("Extract result: " + extractResult);
        }
        
        logWithTimestamp("Setting executable permissions");
        runWithNewProcessNoReturn(true, "chmod 755 " + binary_path);
        
        // Verify binary exists
        if (new File(binary_path).exists()) {
            logWithTimestamp("Binary extracted successfully");
        } else {
            logWithTimestamp("Failed to extract binary");
            throw new IOException("Binary extraction failed");
        }
    }

    void patch() throws IOException {
        logWithTimestamp("Checking binary for required patches");
        final String mkbootfs_path = activity.getFilesDir().getAbsolutePath() + "/mkbootfs";
        AssetsUtil.exportFiles(activity, "mkbootfs", mkbootfs_path);
        
        logWithTimestamp("Patching binary to include mkbootfs tool");
        runWithNewProcessNoReturn(false, "sed -i '/$BB chmod -R 755 tools bin;/i cp -f " + mkbootfs_path + " $AKHOME/tools;' " + binary_path);
        logWithTimestamp("Binary patched successfully");
    }

    void flash(Activity activity) throws IOException {
        logWithTimestamp("Starting kernel flashing process");
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        logWithTimestamp("Setting up environment variables");
        outputStreamWriter.write("export POSTINSTALL=" + activity.getFilesDir().getAbsolutePath() + "\n");
        
        String flashCommand = "sh " + (MainActivity.DEBUG ? "-x " : "") + binary_path + " 3 1 \"" + file_path + "\"&& touch " + activity.getFilesDir().getAbsolutePath() + "/done\nexit\n";
        logWithTimestamp("Executing flash command with root privileges");
        outputStreamWriter.write(flashCommand);
        outputStreamWriter.flush();
        
        String line;
        int lineCount = 0;
        while ((line = bufferedReader.readLine()) != null) {
            // Don't add timestamp to the flash script output to keep it clean
            MainActivity.appendLog(line, activity);
            lineCount++;
            
            // Update progress with meaningful messages based on common output patterns
            if (line.contains("boot.img") || line.contains(".img")) {
                updateProgress("Processing boot image...");
            } else if (line.contains("extract") || line.contains("unzip")) {
                updateProgress("Extracting files...");
            } else if (line.contains("flash") || line.contains("write")) {
                updateProgress("Writing to flash memory...");
            } else if (line.contains("patch")) {
                updateProgress("Patching kernel...");
            } else if (line.contains("permission") || line.contains("chmod")) {
                updateProgress("Setting permissions...");
            }
        }
        
        logWithTimestamp("Flash script completed with " + lineCount + " lines of output");
        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();

        if (!new File(activity.getFilesDir().getAbsolutePath() + "/done").exists()) {
            logWithTimestamp("Flash failed - completion marker not found");
            throw new IOException();
        }
        
        logWithTimestamp("Flash completed successfully");
    }

    void cleanup() throws IOException {
        logWithTimestamp("Cleaning up environment");
        String result = runWithNewProcessReturn(true, "rm -rf " + activity.getFilesDir().getAbsolutePath() + "/*");
        if (result != null && !result.isEmpty()) {
            logWithTimestamp("Cleanup result: " + result);
        }
        logWithTimestamp("Environment cleanup completed");
    }

    void reboot() throws IOException {
        runWithNewProcessNoReturn(true, "svc power reboot");
    }

    void runWithNewProcessNoReturn(boolean su, String cmd) throws IOException {
        runWithNewProcessReturn(su, cmd);
    }

    // Helper methods for UI updates
    private void updateProgress(String message) {
        // Log progress message with timestamp
        logWithTimestamp(message);
        
        activity.runOnUiThread(() -> {
            try {
                if (progressText != null) {
                    progressText.setText(message);
                }
            } catch (Exception e) {
                // Ignore if text view was already destroyed
            }
        });
    }
    
    private void dismissProgressDialog() {
        activity.runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                // Ignore if dialog was already dismissed or activity was destroyed
            }
        });
    }
    
    private void showStatusNotification(int stringResId, boolean isSuccess) {
        activity.runOnUiThread(() -> {
            try {
                View rootView = activity.findViewById(android.R.id.content);
                View notificationView = activity.getLayoutInflater().inflate(R.layout.layout_status_notification, null);
                
                MaterialCardView cardView = notificationView.findViewById(R.id.status_notification_card);
                ImageView statusIcon = notificationView.findViewById(R.id.status_icon);
                TextView statusMessage = notificationView.findViewById(R.id.status_message);
                // Close button removed as it's no longer in the layout
                
                statusMessage.setText(stringResId);
                statusIcon.setImageResource(isSuccess ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_alert);
                
                // Set card color based on status with better contrast
                int colorResId = isSuccess ? R.color.md_theme_light_primaryContainer : R.color.md_theme_light_errorContainer;
                cardView.setCardBackgroundColor(activity.getResources().getColor(colorResId));
                
                // Improve visibility with shadow and elevation
                cardView.setCardElevation(activity.getResources().getDisplayMetrics().density * 8);
                cardView.setStrokeWidth(0);
                
                // Calculate margins once for reuse
                int marginDp = 16;
                int bottomMarginDp = 72; // Larger bottom margin to avoid FAB
                float density = activity.getResources().getDisplayMetrics().density;
                int marginPx = (int)(marginDp * density);
                int bottomMarginPx = (int)(bottomMarginDp * density);
                
                // Add to the root layout - safely handle different view types
                ViewGroup rootLayout;
                
                if (rootView instanceof CoordinatorLayout) {
                    rootLayout = (CoordinatorLayout) rootView;
                    CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(
                            CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                            CoordinatorLayout.LayoutParams.WRAP_CONTENT);
                    params.gravity = Gravity.BOTTOM | Gravity.END;
                    params.setMargins(marginPx, marginPx, marginPx, bottomMarginPx); // Add margin to avoid FAB
                    rootLayout.addView(notificationView, params);
                } else if (rootView instanceof ViewGroup) {
                    rootLayout = (ViewGroup) rootView;
                    ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(marginPx, marginPx, marginPx, bottomMarginPx); // Add margin to avoid FAB
                    notificationView.setLayoutParams(params);
                    
                    // Position at bottom-right
                    notificationView.setX(rootView.getWidth() - notificationView.getWidth() - marginPx);
                    notificationView.setY(rootView.getHeight() - notificationView.getHeight() - bottomMarginPx);
                    
                    rootLayout.addView(notificationView);
                } else {
                    // Fallback to showing a Toast if we can't add the view
                    Toast.makeText(activity, stringResId, Toast.LENGTH_LONG).show();
                    return;
                }
            
                // Enhanced animation with smoother transitions
                cardView.setVisibility(View.VISIBLE);
                cardView.setAlpha(0f);
                cardView.setTranslationX(100f);
                cardView.setTranslationY(20f);
                cardView.setScaleX(0.95f);
                cardView.setScaleY(0.95f);
                cardView.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();
                
                // Set up auto-dismiss with enhanced animation
                cardView.postDelayed(() -> {
                    cardView.animate()
                            .alpha(0f)
                            .translationX(50f)
                            .translationY(20f)
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(450)
                            .setInterpolator(new AccelerateInterpolator(0.8f))
                            .withEndAction(() -> {
                                if (rootLayout != null) {
                                    try {
                                        rootLayout.removeView(notificationView);
                                    } catch (Exception e) {
                                        // Ignore if view was already removed
                                    }
                                }
                            })
                            .start();
                }, 5000);
                
                // No close button needed anymore - removed as requested
            } catch (Exception e) {
                // Fallback to Toast if anything goes wrong
                Toast.makeText(activity, stringResId, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    String runWithNewProcessReturn(boolean su, String cmd) throws IOException {
        Process process = null;
        if (su) {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
        } else {
            process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        }
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write(cmd + "\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        String tmp;
        StringBuilder ret = new StringBuilder();
        while ((tmp = bufferedReader.readLine()) != null) {
            ret.append(tmp);
            ret.append("\n");
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
        return ret.toString();
    }

}
