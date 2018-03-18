package com.github.martoreto.aauto.vex.generic;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.github.martoreto.aauto.vex.FieldSchema;
import com.github.martoreto.aauto.vex.ICarStats;
import com.github.martoreto.aauto.vex.ICarStatsListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.HashMap;
import java.util.Map;

public class GenericCarStatsService extends Service {
    private static final String TAG = "GenericCarStatsService";

    private static final Map<String, FieldSchema> SCHEMA;

    public static final String KEY_LON = "location.lon";
    public static final String KEY_LAT = "location.lat";
    public static final String KEY_PROVIDER = "location.provider";

    static final String ACTION_INIT_LOCATION = "init_location";

    static {
        SCHEMA = new HashMap<>();
        SCHEMA.put(KEY_LON, new FieldSchema(FieldSchema.TYPE_FLOAT, "Longitude", "deg", -180.0f, 180.0f, 0.000000001f));
        SCHEMA.put(KEY_LAT, new FieldSchema(FieldSchema.TYPE_FLOAT, "Latitude", "deg", -90.0f, 90.0f, 0.000000001f));
        SCHEMA.put(KEY_PROVIDER, new FieldSchema(FieldSchema.TYPE_STRING, "Provider", null, 0, 0, 0));
    }

    private final Handler mHandler = new Handler();
    private RemoteCallbackList<ICarStatsListener> mListeners = new RemoteCallbackList<>();
    private FusedLocationProviderClient mFusedLocationClient;
    private Map<String, Object> mMeasurements = new HashMap<>();
    private boolean mLocationInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter intentFilter = new IntentFilter(ACTION_INIT_LOCATION);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, intentFilter);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initLocation();
    }

    private final void initLocation() {
        if (mLocationInitialized) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            handleLocation(location);
                        }
                    }
                });

        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null /* Looper */);

        mLocationInitialized = true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_INIT_LOCATION.equals(intent.getAction())) {
                initLocation();
            }
        }
    };

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);

        if (mLocationInitialized) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
        mFusedLocationClient = null;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final ICarStats.Stub mBinder = new ICarStats.Stub() {
        @Override
        public void registerListener(final ICarStatsListener listener) throws RemoteException {
            mListeners.register(listener);
        }

        @Override
        public void unregisterListener(final ICarStatsListener listener) throws RemoteException {
            mListeners.unregister(listener);
        }

        @Override
        public Map getMergedMeasurements() throws RemoteException {
            return mMeasurements;
        }

        @Override
        public boolean needsPermissions() throws RemoteException {
            return ContextCompat.checkSelfPermission(GenericCarStatsService.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public void requestPermissions() throws RemoteException {
            Intent i = new Intent(GenericCarStatsService.this, PermissionsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            GenericCarStatsService.this.startActivity(i);
        }

        @Override
        public Map getSchema() throws RemoteException {
            return SCHEMA;
        }
    };

    private void dispatchMeasurements(long ts, Map<String, Object> values) {
        try {
            final int n = mListeners.beginBroadcast();
            for (int i = 0; i < n; i++) {
                ICarStatsListener listener = mListeners.getBroadcastItem(i);
                try {
                    listener.onNewMeasurements(ts, values);
                } catch (RemoteException re) {
                    // ignore
                }
            }
        } finally {
            mListeners.finishBroadcast();
        }
    }

    private final LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location loc: locationResult.getLocations()) {
                handleLocation(loc);
            }
        }
    };

    private void handleLocation(Location loc) {
        Log.v(TAG, "Location: " + loc);
        Map<String, Object> values = new HashMap<>();
        values.put(KEY_LAT, (float)loc.getLatitude());
        values.put(KEY_LON, (float)loc.getLongitude());
        values.put(KEY_PROVIDER, loc.getProvider());
        dispatchMeasurements(loc.getTime(), values);
        mMeasurements = values;
    }
}
