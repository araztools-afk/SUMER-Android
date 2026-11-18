package com.araztools.sumer.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.araztools.sumer.data.local.DeviceHistoryDao;
import com.araztools.sumer.data.local.DeviceHistoryEntity;
import com.araztools.sumer.data.local.SumerDatabase;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DeviceHistoryStore {
    public interface DevicesCallback {
        void onLoaded(@NonNull List<DeviceHistoryEntity> devices);
    }

    private final DeviceHistoryDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DeviceHistoryStore(@NonNull SumerDatabase database) {
        dao = database.deviceHistoryDao();
    }

    public void record(
        String name,
        String address,
        int baudRate,
        String result,
        boolean connected
    ) {
        if (address == null || address.trim().isEmpty()) return;

        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                DeviceHistoryEntity device = dao.findByAddress(address);
                if (device == null) {
                    device = new DeviceHistoryEntity();
                    device.address = address;
                    device.name = "";
                    device.alias = "";
                    device.connectionCount = 0;
                    device.lastConnectedAt = 0L;
                }

                if (name != null && !name.trim().isEmpty()) {
                    device.name = name;
                }
                if (device.alias == null) device.alias = "";
                device.lastAttemptAt = now;
                device.baudRate = baudRate;
                device.lastResult = result == null ? "" : result;

                if (connected) {
                    device.lastConnectedAt = now;
                    device.connectionCount++;
                }

                dao.upsert(device);
            } catch (Exception ignored) {
                // Device history must not interrupt Bluetooth acquisition.
            }
        });
    }

    public void loadRecentDevices(@NonNull DevicesCallback callback) {
        executor.execute(() -> {
            List<DeviceHistoryEntity> devices;
            try {
                devices = dao.getRecentDevices();
            } catch (Exception ignored) {
                devices = Collections.emptyList();
            }

            final List<DeviceHistoryEntity> result = devices;
            mainHandler.post(() -> callback.onLoaded(result));
        });
    }

    public void updateAlias(String address, String alias) {
        if (address == null || address.trim().isEmpty()) return;
        executor.execute(() -> {
            try {
                dao.updateAlias(address, alias == null ? "" : alias.trim());
            } catch (Exception ignored) {
            }
        });
    }

    public void close() {
        executor.shutdown();
    }
}
