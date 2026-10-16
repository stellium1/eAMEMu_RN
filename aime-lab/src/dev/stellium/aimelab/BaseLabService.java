package dev.stellium.aimelab;

import android.nfc.cardemulation.HostNfcFService;
import android.os.Bundle;

public abstract class BaseLabService extends HostNfcFService {
    private FelicaProtocol protocol;
    @Override public void onCreate() {
        super.onCreate();
        protocol = new FelicaProtocol(LabStore.identity(this));
    }
    @Override public byte[] processNfcFPacket(byte[] packet, Bundle extras) {
        // A service can survive an activity or identity restore. Refresh from the small local preference.
        protocol = new FelicaProtocol(LabStore.identity(this));
        byte[] response = protocol.respond(packet);
        LabStore.log("RX " + LabStore.packetSummary(packet) + " → TX " + LabStore.packetSummary(response));
        return response;
    }
    @Override public void onDeactivated(int reason) { LabStore.log("NFC 연결 해제 reason=" + reason); }
}
