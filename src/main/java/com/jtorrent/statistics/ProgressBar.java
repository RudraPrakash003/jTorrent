package com.jtorrent.statistics;

public class ProgressBar {
    private static final int BAR_WIDTH = 40;
    private static final long startTime = System.currentTimeMillis();

    public  static void showProgressBar(double downloaded, long totalSize) {
        double progress = downloaded / totalSize;
        int completed = (int) (progress * BAR_WIDTH);

        long elapsed = System.currentTimeMillis() - startTime;
        double speed = downloaded / (elapsed / 1000.0);

        String bar = "=".repeat(completed) + " ".repeat(BAR_WIDTH - completed);
        System.out.print("\r[" + bar + "]" + String.format("%.2f%% | %.2f KB/s", progress * 100, speed / 1024));
    }
}
