package xzr.hkf.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for handling operations with assets
 */
public class AssetsUtil {
    private static final String TAG = "AssetsUtil";

    /**
     * Export a file from assets to a specific location on the filesystem
     * 
     * @param context Application context
     * @param assetName Name of the asset file to export
     * @param outputPath Full path where the asset should be exported to
     * @return True if export was successful, false otherwise
     */
    public static boolean exportFiles(Context context, String assetName, String outputPath) {
        try {
            InputStream inputStream = context.getAssets().open(assetName);
            File outFile = new File(outputPath);
            
            // Create parent directories if they don't exist
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }
            
            try (OutputStream outputStream = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int read;
                
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                
                outputStream.flush();
            }
            
            inputStream.close();
            
            // Make file executable if it's a binary or script
            if (assetName.endsWith(".sh") || !assetName.contains(".")) {
                outFile.setExecutable(true);
            }
            
            Log.d(TAG, "Asset exported successfully: " + assetName + " -> " + outputPath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to export asset: " + assetName, e);
            return false;
        }
    }
}
