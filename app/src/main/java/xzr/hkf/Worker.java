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
import com.google.android.material.snackbar.Snackbar;

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

        // Create backup before proceeding
        updateProgress("Creating kernel backup...");
        try {
            createBackup();
        } catch (IOException ioException) {
            logWithTimestamp("Warning: Failed to create backup - " + ioException.getMessage());
            // Show warning but continue with flashing
            activity.runOnUiThread(() -> {
                Snackbar.make(activity.findViewById(R.id.fab_flash), 
                    R.string.backup_warning, Snackbar.LENGTH_LONG)
                    .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                    .show();
            });
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
        
        // Check if it's a valid AnyKernel3 zip by verifying that it contains required files
        logWithTimestamp("Verifying AnyKernel3 zip contents");
        String checkAnyKernel = runWithNewProcessReturn(true, "unzip -l " + file_path + " | grep -E 'anykernel.sh|META-INF/com/google/android/update-binary'");
        
        if (checkAnyKernel == null || 
            !checkAnyKernel.contains("anykernel.sh") || 
            !checkAnyKernel.contains("META-INF/com/google/android/update-binary")) {
            
            logWithTimestamp("Invalid AnyKernel3 zip! Essential files missing");
            is_error = true;
            return;
        }
        
        logWithTimestamp("AnyKernel3 zip verification successful");
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

    // Helper method to get the boot partition path
    private String getBootPartition() throws IOException {
        // First, let's log all partitions for debugging
        logWithTimestamp("Searching for boot partition...");
        String allPartitions = runWithNewProcessReturn(true, "ls -la /dev/block/*/*/by-name/ 2>/dev/null");
        logWithTimestamp("Available partitions: " + allPartitions);
        
        // Get current boot slot for A/B devices
        String currentSlot = runWithNewProcessReturn(true, "getprop ro.boot.slot_suffix").trim();
        logWithTimestamp("Current boot slot: " + currentSlot);
        
        // First try to find boot partition name directly from logs
        if (allPartitions != null && !allPartitions.isEmpty()) {
            if (allPartitions.contains("boot_a") || allPartitions.contains("boot_b")) {
                logWithTimestamp("Device uses A/B partition scheme");
                
                // Try to match specific boot partition based on current slot
                String bootPartitionName = "boot" + (currentSlot.isEmpty() ? "_a" : currentSlot);
                logWithTimestamp("Looking for partition: " + bootPartitionName);
                
                // Extract direct device path from partition listing
                String[] lines = allPartitions.split("\n");
                for (String line : lines) {
                    if (line.contains(bootPartitionName + " ->")) {
                        String blockDevice = line.substring(line.lastIndexOf("->") + 2).trim();
                        logWithTimestamp("Found boot partition device: " + blockDevice);
                        return blockDevice;
                    }
                }
                
                // If we can't find the exact boot partition, try checking by fixed paths
                String[] possibleABPaths = {
                    "/dev/block/by-name/" + bootPartitionName,
                    "/dev/block/bootdevice/by-name/" + bootPartitionName,
                    "/dev/block/platform/*/by-name/" + bootPartitionName
                };
                
                for (String path : possibleABPaths) {
                    logWithTimestamp("Checking A/B path: " + path);
                    String result = runWithNewProcessReturn(true, "ls -l " + path + " 2>/dev/null");
                    if (result != null && !result.isEmpty() && !result.contains("No such file")) {
                        if (result.contains("->")) {
                            String[] parts = result.split("->");
                            if (parts.length > 1) {
                                String bootPath = parts[1].trim();
                                logWithTimestamp("Found A/B boot partition symlink: " + bootPath);
                                return bootPath;
                            }
                        }
                        logWithTimestamp("Found A/B boot partition: " + path);
                        return path;
                    }
                }
                
                // Last resort for A/B: try direct hardcoded paths based on logs
                if (allPartitions.contains("boot_a -> /dev/block/sda13")) {
                    String bootPath = currentSlot.equals("_b") ? "/dev/block/sda23" : "/dev/block/sda13";
                    logWithTimestamp("Using direct block device based on logs: " + bootPath);
                    return bootPath;
                }
            }
        }
        
        // Try common boot partition locations (for non-A/B devices)
        String[] possibleBootLocations = {
                "/dev/block/by-name/boot",
                "/dev/block/bootdevice/by-name/boot",
                "/dev/block/platform/*/by-name/boot"
        };
        
        for (String location : possibleBootLocations) {
            logWithTimestamp("Checking location: " + location);
            
            String result = runWithNewProcessReturn(true, "ls -l " + location + " 2>/dev/null");
            logWithTimestamp("Result: " + result);
            
            if (result != null && !result.isEmpty() && !result.contains("No such file")) {
                if (result.contains("->")) {
                    String[] parts = result.split("->");
                    if (parts.length > 1) {
                        String path = parts[1].trim();
                        logWithTimestamp("Found boot partition symlink: " + path);
                        return path;
                    }
                }
                logWithTimestamp("Found boot partition: " + location);
                return location;
            }
        }
        
        // Try find command - look for both boot and boot_a/boot_b
        logWithTimestamp("Trying alternative detection method...");
        String findCommand = "find /dev/block -type l -name 'boot*' 2>/dev/null | head -1";
        logWithTimestamp("Running find command: " + findCommand);
        String foundBoot = runWithNewProcessReturn(true, findCommand);
        
        if (foundBoot != null && !foundBoot.isEmpty()) {
            logWithTimestamp("Found boot partition via find: " + foundBoot.trim());
            return foundBoot.trim();
        }
        
        // If all else fails and we know there's an A/B system from the logs above
        if (allPartitions != null && allPartitions.contains("boot_a")) {
            logWithTimestamp("Falling back to hardcoded boot_a");
            return "/dev/block/by-name/boot_a";
        }
        
        logWithTimestamp("Warning: Using default fallback boot partition path");
        return "/dev/block/bootdevice/by-name/boot";
    }
    
    // Create backup of the boot image before flashing
    void createBackup() throws IOException {
        try {
            // Create backup directory if it doesn't exist
            File backupDir = new File(activity.getFilesDir(), "backups");
            if (!backupDir.exists()) {
                boolean dirCreated = backupDir.mkdirs();
                if (!dirCreated) {
                    logWithTimestamp("Warning: Failed to create backup directory");
                }
            }
            
            // Create timestamped backup file
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            String timestamp = dateFormat.format(new Date());
            
            // Only backup boot partition during auto backup
            String bootPartition = getBootPartition();
            if (bootPartition == null) {
                throw new IOException("Unable to determine boot partition");
            }
            
            // Create boot backup file
            File backupFile = new File(backupDir, "boot_backup_" + timestamp + ".img");
            
            logWithTimestamp("Backing up boot partition: " + bootPartition);
            logWithTimestamp("Backup location: " + backupFile.getAbsolutePath());
            
            // Use dd to dump the boot partition with better error handling
            String ddCommand = "dd if=" + bootPartition + " of=" + backupFile.getAbsolutePath() + " bs=4096";
            logWithTimestamp("Running command: " + ddCommand);
            String result = runWithNewProcessReturn(true, ddCommand);
            
            logWithTimestamp("DD command result: " + result);
            
            if (!backupFile.exists()) {
                logWithTimestamp("Error: Backup file was not created");
                throw new IOException("Backup file does not exist after dd command");
            }
            
            if (backupFile.length() == 0) {
                logWithTimestamp("Error: Backup file is empty");
                throw new IOException("Backup file is empty (0 bytes)");
            }
            
            // Save metadata about this backup
            saveBackupMetadata(backupFile.getName(), "boot", timestamp);
            
            logWithTimestamp("Backup created successfully (" + (backupFile.length() / 1024) + " KB)");
            
            // Save the path to the most recent backup
            setLatestBackupPath(backupFile.getAbsolutePath());
        } catch (Exception e) {
            logWithTimestamp("Backup creation failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Backup failed: " + e.getMessage());
        }
    }
    
    // Multi-partition backup with external storage support
    boolean createMultiPartitionBackup(String[] partitionNames, boolean useExternalStorage) {
        boolean allSuccessful = true;
        int successCount = 0;
        int failCount = 0;
        
        try {
            // Create timestamped folder name
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            String timestamp = dateFormat.format(new Date());
            
            // Determine backup directory
            File backupDir;
            if (useExternalStorage) {
                // Use primary external storage (sdcard/HorizonRevamped/backups/)
                File externalDir = new File(android.os.Environment.getExternalStorageDirectory(), "HorizonRevamped/backups/" + timestamp);
                if (!externalDir.exists()) {
                    boolean created = externalDir.mkdirs();
                    if (!created) {
                        logWithTimestamp("Failed to create external backup directory");
                        return false;
                    }
                }
                backupDir = externalDir;
            } else {
                // Use internal app storage
                backupDir = new File(activity.getFilesDir(), "backups/" + timestamp);
                if (!backupDir.exists()) {
                    boolean created = backupDir.mkdirs();
                    if (!created) {
                        logWithTimestamp("Failed to create internal backup directory");
                        return false;
                    }
                }
            }
            
            logWithTimestamp("Creating backups in: " + backupDir.getAbsolutePath());
            
            // Back up each selected partition
            for (String partitionName : partitionNames) {
                try {
                    // Get partition path based on name
                    String partitionPath = getPartitionPath(partitionName);
                    if (partitionPath == null) {
                        logWithTimestamp("Warning: Unable to find " + partitionName + " partition");
                        failCount++;
                        continue;
                    }
                    
                    // Create backup file for this partition
                    File backupFile = new File(backupDir, partitionName + ".img");
                    
                    logWithTimestamp("Backing up " + partitionName + " partition: " + partitionPath);
                    logWithTimestamp("Backup location: " + backupFile.getAbsolutePath());
                    
                    // Use dd to create backup
                    String ddCommand = "dd if=" + partitionPath + " of=" + backupFile.getAbsolutePath() + " bs=4096";
                    logWithTimestamp("Running command: " + ddCommand);
                    String result = runWithNewProcessReturn(true, ddCommand);
                    
                    logWithTimestamp("DD command result for " + partitionName + ": " + result);
                    
                    if (!backupFile.exists() || backupFile.length() == 0) {
                        logWithTimestamp("Error: " + partitionName + " backup file is empty or doesn't exist");
                        failCount++;
                    } else {
                        // Save metadata about this backup
                        savePartitionBackupMetadata(backupFile.getName(), partitionName, partitionPath, timestamp, backupDir);
                        logWithTimestamp(partitionName + " backup created successfully (" + (backupFile.length() / 1024) + " KB)");
                        successCount++;
                    }
                } catch (Exception e) {
                    logWithTimestamp(partitionName + " backup failed: " + e.getMessage());
                    failCount++;
                }
            }
            
            // Create a summary file
            try {
                File summaryFile = new File(backupDir, "backup_summary.txt");
                FileOutputStream fos = new FileOutputStream(summaryFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos);
                writer.write("# HorizonRevamped Backup Summary\n");
                writer.write("Timestamp: " + timestamp + "\n");
                writer.write("Success: " + successCount + "\n");
                writer.write("Failed: " + failCount + "\n");
                writer.write("Partitions: " + String.join(", ", partitionNames) + "\n");
                writer.flush();
                writer.close();
            } catch (Exception e) {
                logWithTimestamp("Warning: Failed to write backup summary - " + e.getMessage());
            }
            
            // Return true only if all backups were successful
            return failCount == 0;
            
        } catch (Exception e) {
            logWithTimestamp("Multi-partition backup failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Helper method to get partition path based on name
    private String getPartitionPath(String partitionName) throws IOException {
        // Log all partitions for debugging
        logWithTimestamp("Looking for " + partitionName + " partition...");
        
        // For boot partition, use the specialized method
        if (partitionName.equals("boot")) {
            return getBootPartition();
        }
        
        // Get current slot for A/B devices
        String currentSlot = runWithNewProcessReturn(true, "getprop ro.boot.slot_suffix").trim();
        logWithTimestamp("Current slot: " + currentSlot);
        
        // Get all partitions listing for reference
        String allPartitions = runWithNewProcessReturn(true, "ls -la /dev/block/*/*/by-name/ 2>/dev/null");
        
        // For special case of system_dlkm (exact match is important)
        if (partitionName.equals("system_dlkm")) {
            String partitionWithSlot = "system_dlkm" + currentSlot;
            logWithTimestamp("Looking for exact " + partitionWithSlot + " partition");
            
            // Try direct paths first with exact match
            String[] possiblePaths = {
                "/dev/block/by-name/" + partitionWithSlot,
                "/dev/block/bootdevice/by-name/" + partitionWithSlot,
                "/dev/block/mapper/" + partitionWithSlot
            };
            
            for (String path : possiblePaths) {
                String checkResult = runWithNewProcessReturn(true, "ls -l " + path + " 2>/dev/null");
                if (checkResult != null && !checkResult.isEmpty() && !checkResult.contains("No such file")) {
                    logWithTimestamp("Found system_dlkm partition at: " + path);
                    return path;
                }
            }
            
            // Try find command specifically for system_dlkm
            String findCmd = "find /dev/block -name \"system_dlkm*\" -o -name \"system_dlkm" + currentSlot + "\" 2>/dev/null | head -1";
            String result = runWithNewProcessReturn(true, findCmd);
            if (result != null && !result.isEmpty()) {
                logWithTimestamp("Found system_dlkm via find: " + result.trim());
                return result.trim();
            }
        }
        
        // Special case for vendor_boot (exact match is important)
        if (partitionName.equals("vendor_boot")) {
            String partitionWithSlot = "vendor_boot" + currentSlot;
            logWithTimestamp("Looking for exact " + partitionWithSlot + " partition");
            
            // Parse the partition listing to find exact matches
            if (allPartitions != null && !allPartitions.isEmpty()) {
                String[] lines = allPartitions.split("\n");
                for (String line : lines) {
                    if (line.contains(partitionWithSlot + " ->")) {
                        String blockDevice = line.substring(line.lastIndexOf("->") + 2).trim();
                        logWithTimestamp("Found vendor_boot partition: " + blockDevice);
                        return blockDevice;
                    }
                }
            }
            
            // Try direct paths
            String[] possiblePaths = {
                "/dev/block/by-name/" + partitionWithSlot,
                "/dev/block/bootdevice/by-name/" + partitionWithSlot
            };
            
            for (String path : possiblePaths) {
                String checkResult = runWithNewProcessReturn(true, "ls -l " + path + " 2>/dev/null");
                if (checkResult != null && !checkResult.isEmpty() && !checkResult.contains("No such file")) {
                    logWithTimestamp("Found vendor_boot partition at: " + path);
                    return path;
                }
            }
        }
        
        // Special case for vendor_kernel_boot
        if (partitionName.equals("vendor_kernel_boot")) {
            String partitionWithSlot = "vendor_kernel_boot" + currentSlot;
            logWithTimestamp("Looking for exact " + partitionWithSlot + " partition");
            
            // Parse the partition listing to find exact matches
            if (allPartitions != null && !allPartitions.isEmpty()) {
                String[] lines = allPartitions.split("\n");
                for (String line : lines) {
                    if (line.contains(partitionWithSlot + " ->")) {
                        String blockDevice = line.substring(line.lastIndexOf("->") + 2).trim();
                        logWithTimestamp("Found vendor_kernel_boot partition: " + blockDevice);
                        return blockDevice;
                    }
                }
            }
        }
        
        // Special case for init_boot
        if (partitionName.equals("init_boot")) {
            String partitionWithSlot = "init_boot" + currentSlot;
            logWithTimestamp("Looking for exact " + partitionWithSlot + " partition");
            
            // Parse the partition listing to find exact matches
            if (allPartitions != null && !allPartitions.isEmpty()) {
                String[] lines = allPartitions.split("\n");
                for (String line : lines) {
                    if (line.contains(partitionWithSlot + " ->")) {
                        String blockDevice = line.substring(line.lastIndexOf("->") + 2).trim();
                        logWithTimestamp("Found init_boot partition: " + blockDevice);
                        return blockDevice;
                    }
                }
            }
        }
        
        // For other partitions, first try with slot suffix
        String partitionWithSlot = partitionName + currentSlot;
        logWithTimestamp("Checking for " + partitionWithSlot + " partition");
        
        // Try exactly parsing the partition listing first
        if (allPartitions != null && !allPartitions.isEmpty()) {
            String[] lines = allPartitions.split("\n");
            
            // First, try exact name with slot suffix
            for (String line : lines) {
                if (line.contains(" " + partitionWithSlot + " ->")) {
                    String blockDevice = line.substring(line.lastIndexOf("->") + 2).trim();
                    logWithTimestamp("Found " + partitionWithSlot + " partition: " + blockDevice);
                    return blockDevice;
                }
            }
            
            // Then try without slot suffix
            for (String line : lines) {
                if (line.contains(" " + partitionName + " ->")) {
                    String blockDevice = line.substring(line.lastIndexOf("->") + 2).trim();
                    logWithTimestamp("Found " + partitionName + " partition: " + blockDevice);
                    return blockDevice;
                }
            }
        }
        
        // If listing didn't work, try direct paths
        String[] possibleLocations = {
            "/dev/block/by-name/" + partitionWithSlot,
            "/dev/block/bootdevice/by-name/" + partitionWithSlot,
            "/dev/block/platform/*/by-name/" + partitionWithSlot,
            "/dev/block/by-name/" + partitionName,
            "/dev/block/bootdevice/by-name/" + partitionName,
            "/dev/block/platform/*/by-name/" + partitionName
        };
        
        for (String location : possibleLocations) {
            logWithTimestamp("Checking location: " + location);
            String result = runWithNewProcessReturn(true, "ls -l " + location + " 2>/dev/null");
            
            if (result != null && !result.isEmpty() && !result.contains("No such file")) {
                if (result.contains("->")) {
                    String[] parts = result.split("->");
                    if (parts.length > 1) {
                        String path = parts[1].trim();
                        logWithTimestamp("Found " + partitionName + " symlink: " + path);
                        return path;
                    }
                }
                logWithTimestamp("Found " + partitionName + " partition: " + location);
                return location;
            }
        }
        
        // As a fallback, try simpler find command
        String findCommand = "find /dev/block -type l -name '" + partitionName + "*' 2>/dev/null | head -1";
        logWithTimestamp("Running find command: " + findCommand);
        String foundPartition = runWithNewProcessReturn(true, findCommand);
        
        if (foundPartition != null && !foundPartition.isEmpty()) {
            logWithTimestamp("Found " + partitionName + " via find: " + foundPartition.trim());
            return foundPartition.trim();
        }
        
        // If not found, return null
        logWithTimestamp("Could not find " + partitionName + " partition");
        return null;
    }
    
    // Save metadata specific to a partition backup
    private void savePartitionBackupMetadata(String filename, String partitionName, String partitionPath, 
                                            String timestamp, File backupDir) throws IOException {
        // Create metadata.txt in the backup directory
        File metadataFile = new File(backupDir, "metadata.txt");
        boolean isNewFile = !metadataFile.exists();
        
        FileOutputStream fos = new FileOutputStream(metadataFile, true); // Append mode
        OutputStreamWriter writer = new OutputStreamWriter(fos);
        
        if (isNewFile) {
            writer.write("# HorizonRevamped Backup Metadata\n");
            writer.write("# Format: filename|partition_name|partition_path|timestamp|kernel_version\n");
        }
        
        // Try to get kernel version
        String kernelVersion = runWithNewProcessReturn(false, "uname -r").trim();
        
        writer.write(filename + "|" + partitionName + "|" + partitionPath + "|" + timestamp + "|" + kernelVersion + "\n");
        writer.flush();
        writer.close();
    }
    
    // Update existing method to handle external storage
    private void saveBackupMetadata(String filename, String partitionName, String timestamp) throws IOException {
        File metadataFile = new File(activity.getFilesDir(), "backups/metadata.txt");
        boolean isNewFile = !metadataFile.exists();
        
        FileOutputStream fos = new FileOutputStream(metadataFile, true); // Append mode
        OutputStreamWriter writer = new OutputStreamWriter(fos);
        
        if (isNewFile) {
            writer.write("# HorizonRevamped Backup Metadata\n");
            writer.write("# Format: filename|partition|timestamp|kernel_version\n");
        }
        
        // Try to get kernel version
        String kernelVersion = runWithNewProcessReturn(false, "uname -r").trim();
        
        String bootPartition = getBootPartition();
        writer.write(filename + "|" + bootPartition + "|" + timestamp + "|" + kernelVersion + "\n");
        writer.flush();
        writer.close();
    }
    
    // Save the path of the most recent backup for quick restore
    private void setLatestBackupPath(String path) {
        try {
            File latestFile = new File(activity.getFilesDir(), "backups/latest_backup.txt");
            FileOutputStream fos = new FileOutputStream(latestFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(path);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            logWithTimestamp("Warning: Failed to save latest backup path - " + e.getMessage());
        }
    }
    
    // Method to restore the most recent backup
    void restoreLatestBackup() throws IOException {
        // Get the latest backup path
        File latestFile = new File(activity.getFilesDir(), "backups/latest_backup.txt");
        if (!latestFile.exists()) {
            throw new IOException("No backup available to restore");
        }
        
        // Read the path of the latest backup
        BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(latestFile)));
        String backupPath = reader.readLine();
        reader.close();
        
        if (backupPath == null || backupPath.isEmpty()) {
            throw new IOException("Invalid backup path");
        }
        
        File backupFile = new File(backupPath);
        if (!backupFile.exists()) {
            throw new IOException("Backup file not found: " + backupPath);
        }
        
        // Get the boot partition
        String bootPartition = getBootPartition();
        if (bootPartition == null) {
            throw new IOException("Unable to determine boot partition");
        }
        
        logWithTimestamp("Restoring backup from: " + backupPath);
        logWithTimestamp("Target partition: " + bootPartition);
        
        // Use dd to flash the backup back to the boot partition
        String result = runWithNewProcessReturn(true, 
                "dd if=" + backupPath + " of=" + bootPartition);
        
        if (result == null || result.contains("error")) {
            throw new IOException("Restore failed: " + result);
        }
        
        logWithTimestamp("Backup restored successfully");
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

    /**
     * Restores multiple partitions from a given backup folder
     * @param backupFolder The folder containing the backup
     * @param selectedPartitions Array of partition names to restore
     * @return true if all restores succeeded, false if any failed
     */
    public boolean restoreMultiplePartitions(File backupFolder, String[] selectedPartitions) {
        if (backupFolder == null || !backupFolder.exists() || !backupFolder.isDirectory()) {
            logWithTimestamp("Invalid backup folder specified");
            return false;
        }

        if (selectedPartitions == null || selectedPartitions.length == 0) {
            logWithTimestamp("No partitions selected for restore");
            return false;
        }

        logWithTimestamp("Restoring from backup: " + backupFolder.getName());
        logWithTimestamp("Selected partitions: " + String.join(", ", selectedPartitions));

        boolean allSucceeded = true;
        for (String partitionName : selectedPartitions) {
            logWithTimestamp("Restoring partition: " + partitionName);
            
            // Find the backup file for this partition
            File backupFile = new File(backupFolder, partitionName + ".img");
            if (!backupFile.exists()) {
                logWithTimestamp("Error: Backup file not found for " + partitionName);
                allSucceeded = false;
                continue;
            }

            // Find the target partition path
            String partitionPath = getPartitionPathByName(partitionName);
            if (partitionPath == null) {
                logWithTimestamp("Error: Could not locate " + partitionName + " partition");
                allSucceeded = false;
                continue;
            }

            // Restore the partition
            boolean success = restorePartition(backupFile, partitionPath);
            if (!success) {
                logWithTimestamp("Failed to restore " + partitionName);
                allSucceeded = false;
            } else {
                logWithTimestamp(partitionName + " restored successfully");
            }
        }

        return allSucceeded;
    }

    /**
     * Restores a partition from a backup file
     * @param backupFile The backup image file
     * @param partitionPath The path to the target partition
     * @return true if restore succeeded, false otherwise
     */
    private boolean restorePartition(File backupFile, String partitionPath) {
        if (!backupFile.exists()) {
            logWithTimestamp("Backup file does not exist: " + backupFile.getAbsolutePath());
            return false;
        }

        if (backupFile.length() == 0) {
            logWithTimestamp("Backup file is empty");
            return false;
        }

        // Check if the partition exists
        if (!new File(partitionPath).exists()) {
            logWithTimestamp("Target partition not found: " + partitionPath);
            return false;
        }

        logWithTimestamp("Restoring " + backupFile.getName() + " to " + partitionPath);

        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "sh", "-c", "dd if=" + backupFile.getAbsolutePath() + 
                " of=" + partitionPath + " bs=4096"
            });
            
            // Read output to prevent buffer overflow
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logWithTimestamp(line);
            }
            
            // Read error output
            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = reader.readLine()) != null) {
                logWithTimestamp("Error: " + line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logWithTimestamp("dd command failed with exit code: " + exitCode);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logWithTimestamp("Exception during restore: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the partition path by name
     * @param partitionName The name of the partition (boot, recovery, etc.)
     * @return The full path to the partition device, or null if not found
     */
    private String getPartitionPathByName(String partitionName) {
        // First check common locations
        String[] commonPaths = {
            "/dev/block/by-name/" + partitionName,
            "/dev/block/bootdevice/by-name/" + partitionName,
        };
        
        for (String path : commonPaths) {
            File file = new File(path);
            if (file.exists()) {
                logWithTimestamp("Found " + partitionName + " at " + path);
                return path;
            }
        }
        
        // Try platform glob pattern
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "sh", "-c", "ls -la /dev/block/platform/*/by-name/" + partitionName
            });
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.contains("No such file")) {
                // Extract the path from the ls output
                String[] parts = line.split(" -> ");
                if (parts.length > 0) {
                    String path = parts[0].trim();
                    logWithTimestamp("Found " + partitionName + " at " + path);
                    return path;
                }
            }
        } catch (Exception e) {
            logWithTimestamp("Error searching platform paths: " + e.getMessage());
        }
        
        // Last resort: try to find with find command
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "sh", "-c", "find /dev/block -name " + partitionName + " -type l 2>/dev/null | head -n 1"
            });
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                logWithTimestamp("Found " + partitionName + " at " + line);
                return line;
            }
        } catch (Exception e) {
            logWithTimestamp("Error using find command: " + e.getMessage());
        }
        
        logWithTimestamp("Could not find " + partitionName + " partition");
        return null;
    }

    // Get a list of available backup folders in both internal and external storage
    java.util.List<File> getAvailableBackupFolders() {
        java.util.List<File> backupFolders = new java.util.ArrayList<>();
        
        try {
            // Check internal app backups
            File internalBackupsDir = new File(activity.getFilesDir(), "backups");
            if (internalBackupsDir.exists() && internalBackupsDir.isDirectory()) {
                File[] folders = internalBackupsDir.listFiles(File::isDirectory);
                if (folders != null) {
                    for (File folder : folders) {
                        backupFolders.add(folder);
                    }
                }
            }
            
            // Check external storage backups
            File externalDir = new File(android.os.Environment.getExternalStorageDirectory(), "HorizonRevamped/backups");
            if (externalDir.exists() && externalDir.isDirectory()) {
                File[] folders = externalDir.listFiles(File::isDirectory);
                if (folders != null) {
                    for (File folder : folders) {
                        backupFolders.add(folder);
                    }
                }
            }
        } catch (Exception e) {
            logWithTimestamp("Error finding backup folders: " + e.getMessage());
        }
        
        return backupFolders;
    }
    
    // Get list of available partition backups in a specific folder
    java.util.List<String> getAvailablePartitionBackups(File backupFolder) {
        java.util.List<String> partitions = new java.util.ArrayList<>();
        
        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            return partitions;
        }
        
        // Get list of img files in the folder
        File[] files = backupFolder.listFiles(file -> 
            file.isFile() && file.getName().endsWith(".img"));
        
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                // Extract partition name from filename
                String partitionName;
                if (filename.contains("_")) {
                    // For filenames like boot_backup_date.img
                    partitionName = filename.substring(0, filename.indexOf("_"));
                } else {
                    // For filenames like boot.img
                    partitionName = filename.substring(0, filename.indexOf(".img"));
                }
                
                if (!partitionName.isEmpty() && !partitions.contains(partitionName)) {
                    partitions.add(partitionName);
                }
            }
        }
        
        return partitions;
    }

}
