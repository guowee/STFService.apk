package jp.co.cyberagent.stf.monitor;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import jp.co.cyberagent.stf.io.MessageWritable;
import jp.co.cyberagent.stf.proto.Wire;

import static android.content.Context.ACTIVITY_SERVICE;

public class CpuMonitor extends AbstractMonitor {
    private static final String TAG = "STFCpuMonitor";

    private CpuState state = null;
    private int cpu_usage = 0;
    private String pkg = null;
    private int[] ram_usage = new int[2];


    public CpuMonitor(Context context, MessageWritable writer) {
        super(context, writer);
    }

    @Override
    public void run() {
        Log.i(TAG, "CPU Monitor starting");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                pkg = intent.getData().getEncodedSchemeSpecificPart();
                Log.i(TAG, String.format("Package %s was added", pkg));

                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        cpu_usage = getCpuUsage(pkg);
                        ram_usage = getRamUsage(pkg);
                        state = new CpuState(cpu_usage, ram_usage);
                        report(writer, state);
                    }
                };
                timer.schedule(task, 0, 50);
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        context.registerReceiver(receiver, intentFilter);

        try {
            synchronized (this) {
                while (!isInterrupted()) {
                    wait();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            Log.i(TAG, "Monitor stopping");
            context.unregisterReceiver(receiver);
        }
    }

    @Override
    public void peek(MessageWritable writer) {
        if (state != null) {
            report(writer, state);
        }
    }


    private void report(MessageWritable writer, CpuState state) {
        writer.write(Wire.Envelope.newBuilder().setType(Wire.MessageType.EVENT_CPU)
            .setMessage(Wire.CpuEvent.newBuilder().setConsumed(state.consumed)
                .setShared(state.shared)
                .setStandalone(state.standalone)
                .build()
                .toByteString()).build());
    }


    private static class CpuState {
        private int consumed;
        private int shared;
        private int standalone;

        public CpuState(int cpu_val, int[] ram_val) {
            consumed = cpu_val;
            shared = ram_val[0];
            standalone = ram_val[1];
        }
    }

    public int getCpuUsage(String pkgname) {

        String parameters = null;
        int cpuUsage = 0;

        try {
            String cmd[] = {"/system/bin/sh", "-c", "top -n 1 | grep " + pkgname};
            Process process = Runtime.getRuntime().exec(cmd);

            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((parameters = in.readLine()) != null) {
                parameters = parameters.trim();
                Log.i(TAG, parameters);
                String[] tokens = parameters.split(" +");
                cpuUsage = Integer.parseInt(tokens[2].replaceAll("%+$", ""));
                Log.i(TAG, Integer.toString(cpuUsage));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cpuUsage;
    }


    public int[] getRamUsage(String pkgname) {
        int pss = 0;
        int uss = 0;
        int[] memory_component = new int[2];

        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
        Map<Integer, String> pidMap = new TreeMap<Integer, String>();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
            pidMap.put(runningAppProcessInfo.pid, runningAppProcessInfo.processName);
        }
        Collection<Integer> keys = pidMap.keySet();
        for (int key : keys) {
            int pids[] = new int[1];
            pids[0] = key;
            android.os.Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(pids);
            for (android.os.Debug.MemoryInfo pidMemoryInfo : memoryInfoArray)
                if (pidMap.get(pids[0]).equals(pkgname)) {
                    pss = pidMemoryInfo.getTotalPss();
                    memory_component[0] = pss;
                    uss = pidMemoryInfo.getTotalPrivateDirty();
                    memory_component[1] = uss;
                    Log.i(TAG, String.format("** MEMINFO in pid %d [%s] **\n", pids[0], pidMap.get(pids[0])));
                    Log.i(TAG, " pidMemoryInfo.getTotalPrivateDirty(): " + pidMemoryInfo.getTotalPrivateDirty() + "\n");
                    Log.i(TAG, " pidMemoryInfo.getTotalPss(): " + pidMemoryInfo.getTotalPss() + "\n");
                    Log.i(TAG, " pidMemoryInfo.getTotalSharedDirty(): " + pidMemoryInfo.getTotalSharedDirty() + "\n");
                }
        }
        return memory_component;


    }


}
