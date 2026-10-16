package dev.stellium.aimelab;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Locale;

final class LabStore {
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("mobile_identity", Context.MODE_PRIVATE);
    }
    static synchronized String identity(Context context) {
        String id = preferences(context).getString("idm", null);
        if (FelicaProtocol.validIdentity(id)) return id;
        byte[] random = new byte[8];
        new SecureRandom().nextBytes(random);
        random[0] = 2; random[1] = (byte) 0xfe;
        id = FelicaProtocol.hex(random);
        if (!preferences(context).edit().putString("idm", id).commit()) throw new IllegalStateException("ID 저장 실패");
        return id;
    }
    static synchronized void log(String value) {
        if (events.size() == 100) events.removeFirst();
        events.addLast(String.format(Locale.ROOT, "%tT %s", System.currentTimeMillis(), value));
    }
    static synchronized String report() { return android.text.TextUtils.join("\n", events); }
    static String packetSummary(byte[] bytes) {
        if (bytes == null) return "응답 없음";
        if (bytes.length < 2) return "잘못된 프레임: " + bytes.length + " bytes";
        // Logs deliberately omit the identity and payload; safe to share for basic diagnostics.
        return String.format(Locale.ROOT, "cmd=%02X length=%d", bytes[1] & 255, bytes.length);
    }
}
