package dev.stellium.aimelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.NfcFCardEmulation;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class LabActivity extends Activity {
    private static final int EXPORT = 41, IMPORT = 42, REPORT = 43;
    private static final String[] NAMES = {"Mobile 14 · 기본 실험", "Mobile 17 · 비교 실험", "FF · 기존 PMm 비교"};
    private static final String[] PMMS = {"1F14FFFFFFFFFFFF", "1F17FFFFFFFFFFFF", "FFFFFFFFFFFFFFFF"};
    private static final Class<?>[] SERVICES = {Mobile14Service.class, Mobile17Service.class, ControlService.class};
    private NfcFCardEmulation emulation;
    private NfcAdapter adapter;
    private Spinner profiles;
    private TextView state, identity, log;
    private Button start;
    private boolean active;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            String latest = LabStore.report();
            if (!latest.contentEquals(log.getText())) log.setText(latest);
            handler.postDelayed(this, 600);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(body);
        setContentView(scroll);
        label(body, "Aime Mobile Lab", 26);
        label(body, "새 모바일 카드 실험 · 비루팅 / Shizuku 없음\n실제 게임 로그인은 아직 검증되지 않았습니다.", 15);
        identity = label(body, "", 16);
        identity.setTextIsSelectable(true);
        updateIdentity();
        profiles = new Spinner(this);
        profiles.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, NAMES));
        profiles.setSelection(Math.max(0, Math.min(2, LabStore.preferences(this).getInt("profile", 0))));
        body.addView(profiles);
        state = label(body, "중지됨", 17);
        start = button(body, "모바일 카드 시작", v -> begin());
        button(body, "중지", v -> stop());
        button(body, "NFC 설정", v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        label(body, "화면을 켜고 이 앱을 앞에 둔 상태로 태그하세요.\n기기에서 접근 코드 확인까지만 시험하세요. 데이터 이전 전에는 새 카드로 게임 데이터를 만들지 마세요.", 15);
        label(body, "카드 ID는 앱에 고정 저장됩니다. 앱 삭제·데이터 초기화 전에 백업하세요. 백업 파일은 본인 보관용입니다.", 14);
        button(body, "카드 ID 백업", v -> createDocument(EXPORT, "application/json", "aime-mobile-identity.json"));
        button(body, "카드 ID 복원", v -> {
            stop();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, IMPORT);
        });
        button(body, "진단 기록 저장 · 카드 ID 제외", v -> createDocument(REPORT, "text/plain", "aime-mobile-diagnostic.txt"));
        label(body, "수신 기록\n폴링은 NFC 칩에서 처리하므로 로그가 없어도 폴링 실패라고 단정할 수 없습니다. ‘시작됨’은 Android 등록 성공만 뜻합니다.", 14);
        log = label(body, "", 12);
        log.setTextIsSelectable(true);
    }
    private TextView label(LinearLayout body, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setPadding(0, 10, 0, 10);
        body.addView(view); return view;
    }
    private Button button(LinearLayout body, String text, View.OnClickListener click) {
        Button view = new Button(this); view.setText(text); view.setOnClickListener(click); body.addView(view); return view;
    }
    private void updateIdentity() { identity.setText("이 앱의 IDm: " + LabStore.identity(this) + "\nSystem Code: 4000"); }
    private void begin() {
        if (active) return;
        try {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) throw new IllegalStateException("이 기기는 HCE-F 기능을 제공하지 않습니다.");
            adapter = NfcAdapter.getDefaultAdapter(this);
            if (adapter == null || !adapter.isEnabled()) throw new IllegalStateException("NFC를 먼저 켜 주세요.");
            emulation = NfcFCardEmulation.getInstance(adapter);
            int profile = profiles.getSelectedItemPosition();
            ComponentName component = new ComponentName(this, SERVICES[profile]);
            emulation.disableService(this);
            if (!emulation.registerSystemCodeForService(component, "4000")) throw new IllegalStateException("System Code 등록 실패");
            String idm = LabStore.identity(this);
            if (!emulation.setNfcid2ForService(component, idm)) throw new IllegalStateException("IDm 등록 실패");
            if (!idm.equalsIgnoreCase(emulation.getNfcid2ForService(component)) || !"4000".equalsIgnoreCase(emulation.getSystemCodeForService(component))) throw new IllegalStateException("등록 값 읽기 검증 실패");
            if (!emulation.enableService(this, component)) throw new IllegalStateException("HCE-F 활성화 실패");
            LabStore.preferences(this).edit().putInt("profile", profile).apply();
            active = true;
            profiles.setEnabled(false); start.setEnabled(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            state.setText("시작됨 · Android 등록 확인\n요청 PMm: " + PMMS[profile] + "\n실제 RF 값·게임 수락은 외부 확인 필요");
            LabStore.log("시작 " + NAMES[profile] + " / SC=4000 / PMm=" + PMMS[profile]);
        } catch (Exception e) {
            stop();
            state.setText("시작 실패: " + e.getMessage());
            LabStore.log("시작 실패: " + e.getClass().getSimpleName());
        }
    }
    private void stop() {
        if (emulation != null) {
            try { emulation.disableService(this); } catch (Exception e) { LabStore.log("중지 확인 실패: " + e.getClass().getSimpleName()); }
        }
        if (active) LabStore.log("중지");
        active = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (profiles != null) profiles.setEnabled(true);
        if (start != null) start.setEnabled(true);
        if (state != null) state.setText("중지됨 · 다시 시작하려면 버튼을 누르세요");
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { stop(); handler.removeCallbacks(refresh); super.onPause(); }
    private void createDocument(int request, String mime, String name) {
        stop();
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (request == IMPORT) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    if (in == null) throw new IllegalStateException("파일을 열 수 없습니다.");
                    byte[] buf = new byte[1024]; int count;
                    while ((count = in.read(buf)) != -1) {
                        if (bytes.size() + count > 4096) throw new IllegalArgumentException("백업 파일이 너무 큽니다.");
                        bytes.write(buf, 0, count);
                    }
                }
                JSONObject backup = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                final String id = backup.getString("idm").toUpperCase(java.util.Locale.ROOT);
                final int profile = backup.getInt("profile");
                if (!"aime-mobile-lab-v1".equals(backup.getString("format")) || !FelicaProtocol.validIdentity(id) || profile < 0 || profile >= NAMES.length) throw new IllegalArgumentException("지원하지 않는 카드 ID 백업입니다.");
                new AlertDialog.Builder(this).setTitle("카드 ID 복원").setMessage("현재 ID를 " + id + " 로 바꿉니다. 기존 ID 백업을 보관했는지 확인하세요.").setNegativeButton("취소", null).setPositiveButton("복원", (dialog, which) -> {
                    if (LabStore.preferences(this).edit().putString("idm", id).putInt("profile", profile).commit()) {
                        profiles.setSelection(profile); updateIdentity(); LabStore.log("ID 복원 완료");
                    } else state.setText("ID 저장 실패");
                }).show();
            } else if (request == EXPORT || request == REPORT) {
                String text;
                if (request == EXPORT) {
                    text = new JSONObject().put("format", "aime-mobile-lab-v1").put("idm", LabStore.identity(this)).put("profile", profiles.getSelectedItemPosition()).toString(2);
                } else {
                    text = "Aime Mobile Lab 0.1\n" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE + " / API " + android.os.Build.VERSION.SDK_INT + "\nRequested profile: " + NAMES[profiles.getSelectedItemPosition()] + "\nPolling and over-air PMm are not observable here.\n\n" + LabStore.report();
                }
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new IllegalStateException("파일에 쓸 수 없습니다.");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                state.setText("파일 저장 완료");
            }
        } catch (Exception e) { state.setText("파일 처리 실패: " + e.getMessage()); }
    }
}
