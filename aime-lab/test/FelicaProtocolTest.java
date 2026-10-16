import dev.stellium.aimelab.FelicaProtocol;
import java.util.Arrays;
import java.util.Random;

public final class FelicaProtocolTest {
    private static final String ID = "02FE112233445566";
    private static final FelicaProtocol PROTOCOL = new FelicaProtocol(ID);
    private static void expect(String request, String response) {
        byte[] actual = PROTOCOL.respond(FelicaProtocol.fromHex(request));
        String got = actual == null ? null : FelicaProtocol.hex(actual);
        if (!java.util.Objects.equals(got, response)) throw new AssertionError(request + ": " + got + " != " + response);
    }
    public static void main(String[] args) {
        expect("0A04" + ID, "0B05" + ID + "00");
        expect("0BA4" + ID + "00", "0BA5" + ID + "00");
        expect("0BA4" + ID + "01", null);
        expect("0A0C" + ID, "0D0D" + ID + "014000");
        expect("0D02" + ID + "010B00", "0D03" + ID + "01FFFF");
        expect("1006" + ID + "010B00018000", "0C07" + ID + "01A6");
        expect("0C0A" + ID + "0000", "0C0B" + ID + "FFFF");
        expect("0A04" + "02FE112233445567", null);
        expect("0B04" + ID, null);
        expect("1006" + ID + "010B00018100", null);
        expect("1006" + ID + "010B00010000", null);
        expect("0600FFFF0000", null); // polling is owned by the controller
        if (PROTOCOL.respond(null) != null) throw new AssertionError("null request");
        Random random = new Random(20261017);
        for (int i = 0; i < 50000; i++) {
            byte[] packet = new byte[random.nextInt(260)]; random.nextBytes(packet);
            if (packet.length > 0) packet[0] = (byte) packet.length;
            if (packet.length >= 10) System.arraycopy(FelicaProtocol.fromHex(ID), 0, packet, 2, 8);
            byte[] reply = PROTOCOL.respond(packet);
            if (reply != null && ((reply[0] & 255) != reply.length || !Arrays.equals(Arrays.copyOfRange(reply, 2, 10), FelicaProtocol.fromHex(ID)))) throw new AssertionError("invalid response frame");
        }
        System.out.println("Protocol vectors and 50,000 malformed-frame trials passed.");
    }
}
