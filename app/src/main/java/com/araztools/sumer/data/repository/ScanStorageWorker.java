package com.araztools.sumer.data.repository;

import androidx.annotation.NonNull;

import com.araztools.sumer.data.local.ScanDao;
import com.araztools.sumer.data.local.ScanEntity;
import com.araztools.sumer.data.local.ScanSampleEntity;
import com.araztools.sumer.data.local.SumerDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes scan persistence on one background thread.
 * Numeric readings remain one database row per incoming line.
 */
public final class ScanStorageWorker {
    public interface ErrorListener {
        void onError(@NonNull String message);
    }

    private static final int BATCH_SIZE = 25;
    private static final int MAX_SAMPLES_PER_SCAN = 2500;
    private static final int MAX_RETRIES = 3;

    private final SumerDatabase database;
    private final ScanDao dao;
    private final ErrorListener errorListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    // These fields are only accessed on executor's single worker thread.
    private final ArrayList<Integer> pending = new ArrayList<>(BATCH_SIZE);
    private long activeScanId = -1L;
    private int nextSampleIndex = 0;

    public ScanStorageWorker(
        @NonNull SumerDatabase database,
        @NonNull ErrorListener errorListener
    ) {
        this.database = database;
        this.dao = database.scanDao();
        this.errorListener = errorListener;

        executor.execute(() -> {
            try {
                dao.markOpenScansIncomplete();
            } catch (Exception error) {
                report(error);
            }
        });
    }

    public void beginScan(
        String deviceName,
        String deviceAddress,
        String mode,
        int rows,
        int columns,
        long startedAt
    ) {
        if (!accepting.get()) return;

        executor.execute(() -> {
            try {
                if (activeScanId != -1L && !finishActive(false)) {
                    report("اسکن قبلی ذخیره نشد؛ شروع اسکن جدید متوقف شد تا داده‌ها حذف نشوند.");
                    return;
                }

                ScanEntity scan = new ScanEntity();
                scan.deviceName = deviceName;
                scan.deviceAddress = deviceAddress;
                scan.mode = "MANUAL".equals(mode) ? "MANUAL" : "AUTOMATIC";
                scan.rows = rows;
                scan.columns = columns;
                scan.startedAt = startedAt;
                scan.completedAt = null;
                scan.sampleCount = 0;
                scan.minValue = null;
                scan.maxValue = null;
                scan.isComplete = false;

                activeScanId = dao.insertScan(scan);
                nextSampleIndex = 0;
                pending.clear();
            } catch (Exception error) {
                report(error);
            }
        });
    }

    public void addSample(int value) {
        if (!accepting.get() || value < 0 || value > 5000) return;

        executor.execute(() -> {
            if (activeScanId == -1L) return;

            if (nextSampleIndex + pending.size() >= MAX_SAMPLES_PER_SCAN) {
                report("ظرفیت ذخیره اسکن به ۲۵۰۰ نمونه رسیده است.");
                return;
            }

            pending.add(value);
            if (pending.size() >= BATCH_SIZE) {
                flushPending();
            }
        });
    }

    public void finishScan(boolean completed) {
        if (!accepting.get()) return;

        executor.execute(() -> {
            if (!finishActive(completed)) {
                report("پایان اسکن ثبت نشد؛ داده‌های در صف هنوز ذخیره نشده‌اند.");
            }
        });
    }

    public void shutdown() {
        if (!accepting.compareAndSet(true, false)) return;

        executor.execute(() -> {
            if (activeScanId != -1L && !finishActive(false)) {
                report("بخشی از داده‌های آخر اسکن هنگام بسته‌شدن برنامه ذخیره نشد.");
            }
        });
        executor.shutdown();
    }

    private boolean finishActive(boolean completed) {
        if (activeScanId == -1L) return true;
        if (!flushPending()) return false;

        try {
            dao.finishScan(
                activeScanId,
                completed ? System.currentTimeMillis() : null,
                completed
            );
            activeScanId = -1L;
            nextSampleIndex = 0;
            pending.clear();
            return true;
        } catch (Exception error) {
            report(error);
            return false;
        }
    }

    private boolean flushPending() {
        if (pending.isEmpty()) return true;
        if (activeScanId == -1L) return false;

        final long scanId = activeScanId;
        final int firstIndex = nextSampleIndex;
        final ArrayList<Integer> batch = new ArrayList<>(pending);
        final int batchMin = Collections.min(batch);
        final int batchMax = Collections.max(batch);

        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                database.runInTransaction(() -> {
                    List<ScanSampleEntity> entities = new ArrayList<>(batch.size());
                    for (int i = 0; i < batch.size(); i++) {
                        ScanSampleEntity sample = new ScanSampleEntity();
                        sample.scanId = scanId;
                        sample.sampleIndex = firstIndex + i;
                        sample.value = batch.get(i);
                        entities.add(sample);
                    }

                    dao.insertSamples(entities);
                    dao.updateSummary(
                        scanId,
                        batch.size(),
                        batchMin,
                        batchMax
                    );
                });

                nextSampleIndex += batch.size();
                pending.subList(0, batch.size()).clear();
                return true;
            } catch (Exception error) {
                lastError = error;
                try {
                    Thread.sleep(150L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastError != null) report(lastError);
        return false;
    }

    private void report(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        report(message);
    }

    private void report(String message) {
        try {
            errorListener.onError(message);
        } catch (Exception ignored) {
            // Error reporting must never stop the storage worker.
        }
    }
}
