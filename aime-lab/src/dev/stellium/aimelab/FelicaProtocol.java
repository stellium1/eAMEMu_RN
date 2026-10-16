package dev.stellium.aimelab;

import java.util.Arrays;
import java.util.Locale;

/** Minimal identity-only NFC-F endpoint. No AIC image, payment or authentication services. */
public final class FelicaProtocol {
    private final byte[] idm;
    public FelicaProtocol(String identity) {
        if (!validIdentity(identity)) throw new IllegalArgumentException("Expected 02FE + 12 hex digits");
        idm = fromHex(identity);
    }
    public static boolean validIdentity(String identity) {
        return identity != null && identity.toUpperCase(Locale.ROOT).matches("02FE[0-9A-F]{12}");
    }
    public byte[] respond(byte[] request) {
        if (request == null || request.length < 10 || request.length > 255 || (request[0] & 255) != request.length) return null;
        if (!Arrays.equals(idm, Arrays.copyOfRange(request, 2, 10))) return null;
        int command = request[1] & 255;
        // Observed mobile probe in amdaemon 96acd852..., FUN_14034c440/FUN_14034a3f0.
        // Implement only subcommand 00; this is not a general Mobile FeliCa implementation.
        if (command == 0xa4 && request.length == 11 && request[10] == 0) return response(0xa5, new byte[] {0});
        if (command == 0x04 && request.length == 10) return response(0x05, new byte[] {0});
        if (command == 0x0c && request.length == 10) return response(0x0d, new byte[] {1, 0x40, 0});
        if (command == 0x02 && request.length >= 13) {
            int count = request[10] & 255;
            if (count < 1 || count > 32 || request.length != 11 + 2 * count) return null;
            byte[] missing = new byte[1 + 2 * count];
            Arrays.fill(missing, (byte) 0xff);
            missing[0] = (byte) count;
            return response(0x03, missing);
        }
        if (command == 0x0a && request.length == 12) return response(0x0b, new byte[] {(byte) 0xff, (byte) 0xff});
        if ((command == 0x06 || command == 0x08) && validBlockRequest(request, command == 0x08)) {
            // This mobile-identity probe implements no block services. Never fabricate successful reads.
            return response(command + 1, new byte[] {1, (byte) 0xa6});
        }
        return null;
    }
    private boolean validBlockRequest(byte[] request, boolean write) {
        if (request.length < 14) return false;
        int services = request[10] & 255;
        if (services < 1 || services > 16) return false;
        int pos = 11 + services * 2;
        if (pos >= request.length) return false;
        int blocks = request[pos++] & 255;
        if (blocks < 1 || blocks > 15) return false;
        for (int i = 0; i < blocks; i++) {
            if (pos >= request.length) return false;
            int descriptor = request[pos] & 255;
            if ((descriptor & 0x0f) >= services) return false;
            pos += (descriptor & 0x80) != 0 ? 2 : 3;
            if (pos > request.length) return false;
        }
        return request.length == pos + (write ? blocks * 16 : 0);
    }
    private byte[] response(int command, byte[] payload) {
        byte[] out = new byte[10 + payload.length];
        out[0] = (byte) out.length;
        out[1] = (byte) command;
        System.arraycopy(idm, 0, out, 2, 8);
        System.arraycopy(payload, 0, out, 10, payload.length);
        return out;
    }
    public static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02X", b & 255));
        return out.toString();
    }
}
