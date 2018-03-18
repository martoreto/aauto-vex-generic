package com.github.martoreto.aauto.vex.generic;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.github.martoreto.aauto.vex.VexProxyService;

public class PermissionsActivity extends Activity {
    private static final int REQUEST_PERMISSION = 1;

    @Override
    protected void onStart() {
        super.onStart();

        if (VexProxyService.needsPermissions(this)) {
            requestPermission();
        } else {
            finish();
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (grantResults.length == 1 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
                } else {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(GenericCarStatsService.ACTION_INIT_LOCATION));
                }
                finish();
                break;
        }
    }

}
