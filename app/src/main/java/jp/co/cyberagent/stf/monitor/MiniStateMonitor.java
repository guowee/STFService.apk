package jp.co.cyberagent.stf.monitor;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;

import jp.co.cyberagent.stf.io.MessageWritable;
import jp.co.cyberagent.stf.proto.Wire;

public class MiniStateMonitor extends AbstractMonitor {
    private static final String TAG = "MiniStateMonitor";
    private static final String[] minitouch_cmd = {"ps", "|", "grep", "minitouch"};
    private static final String[] minicap_cmd = {"ps", "|", "grep", "minicap"};

    private String minicap_state = null;
    private String minitouch_state = null;

    private MiniState state = null;

    public MiniStateMonitor(Context context, MessageWritable writer) {
        super(context, writer);
    }

    @Override
    public void run() {
        Log.i(TAG, "Mini Monitor starting");
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            minicap_state = run(minicap_cmd);
            minitouch_state = run(minitouch_cmd);

            if (minicap_state.endsWith("minicap") && minitouch_state.endsWith("minitouch")) {
                state = new MiniState(true, true);
            } else if (minicap_state.endsWith("minicap")) {
                state = new MiniState(true, false);
            } else if (minitouch_state.endsWith("minitouch")) {
                state = new MiniState(false, true);
            } else {
                state = new MiniState(false, false);
            }
            report(writer, state);
        }
    }

    @Override
    public void peek(MessageWritable writer) {
        if (state != null) {
            report(writer, state);
        }
    }

    private void report(MessageWritable writer, MiniStateMonitor.MiniState state) {
        writer.write(Wire.Envelope.newBuilder().setType(Wire.MessageType.EVENT_MINI_STATE)
            .setMessage(Wire.MiniEvent.newBuilder().setMinicap(state.minicap)
                .setMinitouch(state.minitouch)
                .build()
                .toByteString()).build());
    }


    private class MiniState {
        private boolean minicap;
        private boolean minitouch;

        public MiniState(boolean isMinicap, boolean isMinitouch) {
            minicap = isMinicap;
            minitouch = isMinitouch;
        }
    }


    public synchronized String run(String[] cmd) {
        String s = null;
        InputStream in = null;
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            in = p.getInputStream();
            int len = 0;
            while (len == 0) {
                len = in.available();
            }
            if (len != 0) {
                byte[] buffer = new byte[len];
                in.read(buffer);//读取
                s = new String(buffer);
            }
            if (in != null) {
                in.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return s;
    }
}
