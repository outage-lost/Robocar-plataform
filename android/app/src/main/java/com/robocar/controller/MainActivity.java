package com.robocar.controller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQUEST_BT = 20;
    private static final int BG = Color.rgb(9, 15, 22);
    private static final int PANEL = Color.rgb(19, 29, 40);
    private static final int PANEL_ALT = Color.rgb(25, 38, 51);
    private static final int TEXT = Color.rgb(242, 247, 249);
    private static final int MUTED = Color.rgb(166, 184, 193);
    private static final int ACCENT = Color.rgb(82, 215, 178);
    private static final int WARN = Color.rgb(255, 184, 92);
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothAdapter adapter;
    private LinearLayout deviceList;
    private TextView status, selectedLabel, scanButton, connectButton, ledButton;
    private BluetoothDevice selectedDevice;
    private Connection connection;
    private JoystickView driveStick, steerStick;
    private boolean scanning;
    private boolean ledOn;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        IntentFilter events = new IntentFilter();
        events.addAction(BluetoothDevice.ACTION_FOUND);
        events.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, events, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, events);
        requestBluetoothPermissions();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(14), dp(24), dp(14));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(24) + left, dp(14) + top, dp(24) + right, dp(14) + bottom);
            return insets;
        });

        LinearLayout header = row(Gravity.CENTER_VERTICAL);
        TextView title = text("ROBOCAR", 24, TEXT); title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        status = text("Bluetooth desconectado", 15, WARN);
        header.addView(status, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout bluetoothPanel = column();
        bluetoothPanel.setPadding(dp(16), dp(6), dp(16), dp(6));
        bluetoothPanel.setBackground(round(PANEL, dp(14)));
        LinearLayout panelHeader = row(Gravity.CENTER_VERTICAL);
        TextView section = text("CONEXIÓN BLUETOOTH", 14, ACCENT); section.setTypeface(null, android.graphics.Typeface.BOLD);
        panelHeader.addView(section, new LinearLayout.LayoutParams(0, dp(34), 1));
        scanButton = action("BUSCAR DISPOSITIVOS", ACCENT);
        scanButton.setOnClickListener(v -> scan());
        panelHeader.addView(scanButton, new LinearLayout.LayoutParams(dp(190), dp(42)));
        bluetoothPanel.addView(panelHeader);

        selectedLabel = text("Selecciona un dispositivo para conectar", 14, MUTED);
        selectedLabel.setGravity(Gravity.CENTER_VERTICAL);
        bluetoothPanel.addView(selectedLabel, new LinearLayout.LayoutParams(-1, dp(24)));

        ScrollView deviceScroll = new ScrollView(this);
        deviceList = column();
        deviceScroll.addView(deviceList, new ScrollView.LayoutParams(-1, -2));
        bluetoothPanel.addView(deviceScroll, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout connectionActions = row(Gravity.CENTER_VERTICAL);
        connectButton = action("CONECTAR AL AUTO", ACCENT);
        connectButton.setOnClickListener(v -> toggleConnection());
        connectionActions.addView(connectButton, new LinearLayout.LayoutParams(dp(190), dp(42)));
        ledButton = action("LED APAGADO", MUTED);
        ledButton.setOnClickListener(v -> toggleLed());
        connectionActions.addView(ledButton, new LinearLayout.LayoutParams(dp(150), dp(42)));
        TextView hint = text("Bluetooth clásico · SPP", 12, MUTED);
        hint.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        connectionActions.addView(hint, new LinearLayout.LayoutParams(0, dp(42), 1));
        bluetoothPanel.addView(connectionActions);
        root.addView(bluetoothPanel, new LinearLayout.LayoutParams(-1, dp(176)));

        LinearLayout sticks = row(Gravity.CENTER);
        driveStick = new JoystickView(this, "AVANCE / RETROCESO");
        steerStick = new JoystickView(this, "GIRO IZQUIERDA / DERECHA");
        sticks.addView(driveStick, new LinearLayout.LayoutParams(0, -1, 1));
        sticks.addView(steerStick, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(sticks, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestApplyInsets();
        refreshDeviceList();
    }

    private LinearLayout row(int gravity) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(gravity); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); v.setMaxLines(2); return v; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private TextView action(String value, int color) { TextView v = text(value, 12, TEXT); v.setTypeface(null, android.graphics.Typeface.BOLD); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), 0, dp(8), 0); v.setBackground(round(color == ACCENT ? Color.rgb(35, 116, 99) : Color.rgb(46, 62, 73), dp(10))); return v; }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
        else if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BT);
        else loadPairedDevices();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_BT) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadPairedDevices();
            else setStatus("Permiso Bluetooth requerido", WARN);
        }
    }

    private boolean allowed(String permission) { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    private boolean canConnect() { return Build.VERSION.SDK_INT < 31 || allowed(Manifest.permission.BLUETOOTH_CONNECT); }

    private void loadPairedDevices() {
        if (adapter == null || !canConnect()) return;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            for (BluetoothDevice device : bonded) addDevice(device);
            refreshDeviceList();
            if (!devices.isEmpty()) setStatus(devices.size() + " dispositivo(s) disponible(s)", ACCENT);
            else setStatus("Pulsa BUSCAR DISPOSITIVOS", MUTED);
        } catch (SecurityException e) { setStatus("Permiso Bluetooth requerido", WARN); }
    }

    private void scan() {
        if (adapter == null) { toast("Este teléfono no tiene Bluetooth"); return; }
        if (Build.VERSION.SDK_INT >= 31 && !allowed(Manifest.permission.BLUETOOTH_SCAN)) { requestBluetoothPermissions(); return; }
        if (Build.VERSION.SDK_INT < 31 && !allowed(Manifest.permission.ACCESS_FINE_LOCATION)) { requestBluetoothPermissions(); return; }
        if (!adapter.isEnabled()) { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return; }
        if (scanning) { stopScan(); return; }
        loadPairedDevices();
        scanning = true;
        scanButton.setText("DETENER BÚSQUEDA");
        setStatus("Buscando dispositivos...", WARN);
        try { adapter.cancelDiscovery(); adapter.startDiscovery(); } catch (SecurityException e) { stopScan(); setStatus("No se pudo iniciar la búsqueda", WARN); return; }
        main.postDelayed(() -> { if (scanning) stopScan(); }, 12000);
    }

    private void stopScan() {
        try { if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery(); } catch (SecurityException ignored) { }
        scanning = false;
        scanButton.setText("BUSCAR DISPOSITIVOS");
        main.removeCallbacksAndMessages(null);
        refreshDeviceList();
        setStatus(devices.isEmpty() ? "No se encontraron dispositivos" : devices.size() + " dispositivo(s) disponible(s)", devices.isEmpty() ? WARN : ACCENT);
    }

    private void addDevice(BluetoothDevice device) {
        if (device == null || devices.contains(device)) return;
        devices.add(device);
        if (selectedDevice == null && "RoboCar-ESP32".equalsIgnoreCase(safeName(device))) {
            selectedDevice = device;
            if (selectedLabel != null) selectedLabel.setText("Seleccionado: " + safeName(device));
        }
    }
    private String safeName(BluetoothDevice device) { try { return device.getName() == null ? "Dispositivo sin nombre" : device.getName(); } catch (SecurityException e) { return "Dispositivo Bluetooth"; } }
    private String safeAddress(BluetoothDevice device) { try { return device.getAddress(); } catch (SecurityException e) { return "Dirección no disponible"; } }

    private void refreshDeviceList() {
        if (deviceList == null) return;
        deviceList.removeAllViews();
        Collections.sort(devices, (left, right) -> {
            boolean leftIsCar = "RoboCar-ESP32".equalsIgnoreCase(safeName(left));
            boolean rightIsCar = "RoboCar-ESP32".equalsIgnoreCase(safeName(right));
            if (leftIsCar != rightIsCar) return leftIsCar ? -1 : 1;
            return safeName(left).compareToIgnoreCase(safeName(right));
        });
        if (devices.isEmpty()) {
            TextView empty = text("Ningún dispositivo todavía. Si el ESP32 ya está vinculado, pulsa BUSCAR o revisa Bluetooth del teléfono.", 13, MUTED);
            empty.setPadding(dp(12), 0, dp(12), 0); deviceList.addView(empty, new LinearLayout.LayoutParams(-1, dp(58))); return;
        }
        for (BluetoothDevice device : devices) {
            TextView item = text(safeName(device) + "  ·  " + safeAddress(device), 13, TEXT);
            item.setSingleLine(true); item.setEllipsize(android.text.TextUtils.TruncateAt.END); item.setPadding(dp(12), 0, dp(12), 0);
            item.setBackground(round(device.equals(selectedDevice) ? Color.rgb(31, 83, 76) : PANEL_ALT, dp(8)));
            item.setOnClickListener(v -> { selectedDevice = device; selectedLabel.setText("Seleccionado: " + safeName(device)); refreshDeviceList(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48)); params.setMargins(0, 0, 0, dp(4)); deviceList.addView(item, params);
        }
    }

    private void toggleConnection() {
        if (connection != null) { connection.close(); return; }
        if (selectedDevice == null) { toast("Selecciona primero RoboCar-ESP32 en la lista"); return; }
        connection = new Connection(selectedDevice); connectButton.setText("CONECTANDO..."); setStatus("Conectando a " + safeName(selectedDevice), WARN); connection.start();
    }

    private void toggleLed() { ledOn = !ledOn; ledButton.setText(ledOn ? "LED ENCENDIDO" : "LED APAGADO"); if (connection != null) connection.send("L:" + (ledOn ? 1 : 0) + "\n"); }
    private void setStatus(String value, int color) { runOnUiThread(() -> { status.setText(value); status.setTextColor(color); }); }
    private void setConnected() { runOnUiThread(() -> { setStatus("Conectado a RoboCar", ACCENT); connectButton.setText("DESCONECTAR"); }); }
    private void setDisconnected() { runOnUiThread(() -> { setStatus("Bluetooth desconectado", WARN); connectButton.setText("CONECTAR AL AUTO"); driveStick.reset(); steerStick.reset(); }); }
    private void toast(String value) { runOnUiThread(() -> Toast.makeText(this, value, Toast.LENGTH_SHORT).show()); }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) { addDevice(device); refreshDeviceList(); setStatus(devices.size() + " dispositivo(s) encontrado(s)", ACCENT); }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction()) && scanning) stopScan();
        }
    };

    private class Connection extends Thread {
        private final BluetoothDevice device; private BluetoothSocket socket; private OutputStream out;
        Connection(BluetoothDevice value) { device = value; }
        public void run() { try { if (adapter.isDiscovering()) adapter.cancelDiscovery(); socket = device.createRfcommSocketToServiceRecord(SPP_UUID); socket.connect(); out = socket.getOutputStream(); setConnected(); } catch (Exception e) { close(); toast("No se pudo conectar. Comprueba que RoboCar-ESP32 esté encendido y vinculado."); } }
        synchronized void send(String command) { if (out == null) return; try { out.write(command.getBytes("UTF-8")); out.flush(); } catch (IOException e) { close(); } }
        synchronized void close() { try { if (socket != null) socket.close(); } catch (IOException ignored) { } out = null; if (connection == this) { connection = null; setDisconnected(); } }
    }

    private class JoystickView extends View {
        private final Paint paint = new Paint(1); private final String caption; private float x, y;
        JoystickView(Context context, String label) { super(context); caption = label; setFocusable(true); }
        protected void onDraw(Canvas canvas) { float cx = getWidth()/2f, cy = getHeight()/2f, radius = Math.min(getWidth(), getHeight())*.38f; paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(24,37,49)); canvas.drawCircle(cx, cy, radius, paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(Color.rgb(47,76,88)); canvas.drawCircle(cx, cy, radius, paint); paint.setStyle(Paint.Style.FILL); paint.setColor(ACCENT); canvas.drawCircle(cx+x*radius*.72f, cy+y*radius*.72f, radius*.27f, paint); paint.setTextSize(dp(13)); paint.setTextAlign(Paint.Align.CENTER); paint.setColor(MUTED); canvas.drawText(caption, cx, cy+radius+dp(28), paint); }
        public boolean onTouchEvent(MotionEvent event) { float cx=getWidth()/2f, cy=getHeight()/2f, radius=Math.min(getWidth(),getHeight())*.38f; if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL) { reset(); return true; } float dx=(event.getX()-cx)/radius, dy=(event.getY()-cy)/radius, length=(float)Math.sqrt(dx*dx+dy*dy); if(length>1) { dx/=length; dy/=length; } x=Math.max(-1,Math.min(1,dx)); y=Math.max(-1,Math.min(1,dy)); invalidate(); sendDrive(); return true; }
        void reset() { x=0; y=0; invalidate(); sendDrive(); }
        private void sendDrive() { if(connection == null) return; int throttle=Math.round(-driveStick.y*100), steering=Math.round(steerStick.x*100); connection.send("D:"+throttle+":"+steering+":200\n"); }
    }

    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); if (connection != null) connection.close(); unregisterReceiver(receiver); super.onDestroy(); }
}
