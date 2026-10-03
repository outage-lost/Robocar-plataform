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
import android.graphics.Typeface;
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
    private LinearLayout screenRoot, deviceList;
    private TextView status, selectedLabel, scanButton, connectButton;
    private TextView controlStatus, ledButton;
    private BluetoothDevice selectedDevice;
    private Connection connection;
    private JoystickView driveStick, steerStick;
    private boolean scanning;
    private boolean controlScreen;
    private boolean ledOn;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        adapter = BluetoothAdapter.getDefaultAdapter();
        IntentFilter events = new IntentFilter();
        events.addAction(BluetoothDevice.ACTION_FOUND);
        events.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, events, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, events);
        showBluetoothScreen();
        requestBluetoothPermissions();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row(int gravity) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(gravity); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); v.setMaxLines(2); return v; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private TextView action(String value, int color) { TextView v = text(value, 12, TEXT); v.setTypeface(null, Typeface.BOLD); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), 0, dp(8), 0); v.setBackground(round(color, dp(10))); return v; }

    private LinearLayout safeRoot() {
        LinearLayout root = column();
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
        return root;
    }

    private void showBluetoothScreen() {
        controlScreen = false;
        screenRoot = safeRoot();
        LinearLayout header = row(Gravity.CENTER_VERTICAL);
        TextView title = text("ROBOCAR", 24, TEXT); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        status = text("Bluetooth desconectado", 15, WARN);
        header.addView(status, new LinearLayout.LayoutParams(-2, dp(48)));
        screenRoot.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(8), dp(16), dp(8));
        panel.setBackground(round(PANEL, dp(14)));
        LinearLayout panelHeader = row(Gravity.CENTER_VERTICAL);
        TextView heading = text("DISPOSITIVOS BLUETOOTH", 14, ACCENT); heading.setTypeface(null, Typeface.BOLD);
        panelHeader.addView(heading, new LinearLayout.LayoutParams(0, dp(34), 1));
        scanButton = action("BUSCAR DISPOSITIVOS", Color.rgb(35, 116, 99));
        scanButton.setOnClickListener(v -> scan());
        panelHeader.addView(scanButton, new LinearLayout.LayoutParams(dp(190), dp(42)));
        panel.addView(panelHeader);

        selectedLabel = text("Selecciona RoboCar-ESP32 para continuar", 14, MUTED);
        panel.addView(selectedLabel, new LinearLayout.LayoutParams(-1, dp(28)));
        ScrollView scroll = new ScrollView(this);
        deviceList = column();
        scroll.addView(deviceList, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(62)));

        connectButton = action("CONECTAR AL AUTO", Color.rgb(35, 116, 99));
        connectButton.setOnClickListener(v -> toggleConnection());
        panel.addView(connectButton, new LinearLayout.LayoutParams(-1, dp(44)));
        screenRoot.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView help = text("El ESP32 debe estar encendido y vinculado en los ajustes Bluetooth del teléfono.", 12, MUTED);
        help.setGravity(Gravity.CENTER); screenRoot.addView(help, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(screenRoot);
        screenRoot.requestApplyInsets();
        refreshDeviceList();
        if (adapter != null && canConnect()) loadPairedDevices();
    }

    private void showControlScreen() {
        controlScreen = true;
        screenRoot = safeRoot();
        LinearLayout header = row(Gravity.CENTER_VERTICAL);
        controlStatus = text("RoboCar conectado", 13, ACCENT);
        header.addView(controlStatus, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView change = action("CAMBIAR DISPOSITIVO", Color.rgb(46, 62, 73));
        change.setOnClickListener(v -> disconnectToBluetooth());
        header.addView(change, new LinearLayout.LayoutParams(dp(190), dp(42)));
        screenRoot.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout controls = row(Gravity.CENTER);
        driveStick = new JoystickView(this, "AVANCE / RETROCESO", false);
        steerStick = new JoystickView(this, "GIRO IZQUIERDA / DERECHA", true);
        controls.addView(driveStick, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout center = column(); center.setGravity(Gravity.CENTER); center.setPadding(dp(8), 0, dp(8), 0);
        TextView movement = text("CONTROL", 11, MUTED); movement.setGravity(Gravity.CENTER); center.addView(movement, new LinearLayout.LayoutParams(-1, dp(26)));
        ledButton = action("LED\nAPAGADO", Color.rgb(46, 62, 73));
        ledButton.setTextSize(11); ledButton.setOnClickListener(v -> toggleLed());
        center.addView(ledButton, new LinearLayout.LayoutParams(dp(96), dp(64)));
        TextView neutral = text("Suelta para detener", 11, MUTED); neutral.setGravity(Gravity.CENTER); center.addView(neutral, new LinearLayout.LayoutParams(-1, dp(38)));
        controls.addView(center, new LinearLayout.LayoutParams(dp(128), -1));
        controls.addView(steerStick, new LinearLayout.LayoutParams(0, -1, 1));
        screenRoot.addView(controls, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text("Bluetooth SPP · movimiento combinado habilitado", 12, MUTED); footer.setGravity(Gravity.CENTER); screenRoot.addView(footer, new LinearLayout.LayoutParams(-1, dp(28)));
        setContentView(screenRoot);
        screenRoot.requestApplyInsets();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
        else if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BT);
        else loadPairedDevices();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_BT) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadPairedDevices();
            else if (!controlScreen && status != null) setStatus("Permiso Bluetooth requerido", WARN);
        }
    }

    private boolean allowed(String permission) { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    private boolean canConnect() { return Build.VERSION.SDK_INT < 31 || allowed(Manifest.permission.BLUETOOTH_CONNECT); }

    private void loadPairedDevices() {
        if (adapter == null || !canConnect() || deviceList == null) return;
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
        loadPairedDevices(); scanning = true; scanButton.setText("DETENER BÚSQUEDA"); setStatus("Buscando dispositivos...", WARN);
        try { adapter.cancelDiscovery(); adapter.startDiscovery(); } catch (SecurityException e) { stopScan(); setStatus("No se pudo iniciar la búsqueda", WARN); return; }
        main.postDelayed(() -> { if (scanning) stopScan(); }, 12000);
    }

    private void stopScan() {
        try { if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery(); } catch (SecurityException ignored) { }
        scanning = false; if (scanButton != null) scanButton.setText("BUSCAR DISPOSITIVOS"); main.removeCallbacksAndMessages(null); refreshDeviceList();
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
            TextView empty = text("No hay dispositivos. Vincula el ESP32 desde Ajustes Bluetooth o pulsa BUSCAR DISPOSITIVOS.", 13, MUTED);
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
        if (connection != null) { disconnectToBluetooth(); return; }
        if (selectedDevice == null) { toast("Selecciona primero RoboCar-ESP32 en la lista"); return; }
        connection = new Connection(selectedDevice); connectButton.setText("CONECTANDO..."); setStatus("Conectando a " + safeName(selectedDevice), WARN); connection.start();
    }

    private void disconnectToBluetooth() {
        Connection current = connection; connection = null;
        if (current != null) current.closeSilently();
        showBluetoothScreen();
    }

    private void toggleLed() {
        ledOn = !ledOn;
        if (ledButton != null) { ledButton.setText(ledOn ? "LED\nENCENDIDO" : "LED\nAPAGADO"); ledButton.setBackground(round(ledOn ? Color.rgb(35, 116, 99) : Color.rgb(46, 62, 73), dp(10))); }
        if (connection != null) connection.send("L:" + (ledOn ? 1 : 0) + "\n");
    }

    private void setStatus(String value, int color) { runOnUiThread(() -> { if (status != null) { status.setText(value); status.setTextColor(color); } if (controlStatus != null) controlStatus.setText(value); }); }
    private void toast(String value) { runOnUiThread(() -> Toast.makeText(this, value, Toast.LENGTH_SHORT).show()); }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !controlScreen) { addDevice(device); refreshDeviceList(); setStatus(devices.size() + " dispositivo(s) encontrado(s)", ACCENT); }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction()) && scanning) stopScan();
        }
    };

    private class Connection extends Thread {
        private final BluetoothDevice device; private BluetoothSocket socket; private OutputStream out;
        Connection(BluetoothDevice value) { device = value; }
        public void run() {
            try {
                if (adapter.isDiscovering()) adapter.cancelDiscovery();
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID); socket.connect(); out = socket.getOutputStream();
                runOnUiThread(() -> showControlScreen());
            } catch (Exception e) { close(); toast("No se pudo conectar. Comprueba que RoboCar-ESP32 esté encendido y vinculado."); }
        }
        synchronized void send(String command) { if (out == null) return; try { out.write(command.getBytes("UTF-8")); out.flush(); } catch (IOException e) { close(); } }
        synchronized void closeSilently() { try { if (socket != null) socket.close(); } catch (IOException ignored) { } out = null; }
        synchronized void close() { closeSilently(); if (connection == this) { connection = null; runOnUiThread(() -> showBluetoothScreen()); } }
    }

    private class JoystickView extends View {
        private final Paint paint = new Paint(1); private final String caption; private final boolean horizontalOnly; private float x, y;
        JoystickView(Context context, String label, boolean horizontal) { super(context); caption = label; horizontalOnly = horizontal; setFocusable(true); }
        protected void onDraw(Canvas canvas) {
            float cx = getWidth()/2f, cy = getHeight()/2f, radius = Math.min(getWidth(), getHeight())*.40f;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(24,37,49)); canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(Color.rgb(47,76,88)); canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(ACCENT); canvas.drawCircle(cx+x*radius*.72f, cy+y*radius*.72f, radius*.27f, paint);
            paint.setTextSize(dp(13)); paint.setTextAlign(Paint.Align.CENTER); paint.setColor(MUTED); canvas.drawText(caption, cx, cy+radius+dp(30), paint);
        }
        public boolean onTouchEvent(MotionEvent event) {
            float cx=getWidth()/2f, cy=getHeight()/2f, radius=Math.min(getWidth(),getHeight())*.40f;
            if (event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL) { reset(); return true; }
            float dx=(event.getX()-cx)/radius, dy=(event.getY()-cy)/radius;
            if (horizontalOnly) dy=0; else dx=0;
            float length=(float)Math.sqrt(dx*dx+dy*dy); if(length>1){dx/=length;dy/=length;}
            if (Math.abs(dx)<.08f) dx=0; if (Math.abs(dy)<.08f) dy=0;
            x=Math.max(-1,Math.min(1,dx)); y=Math.max(-1,Math.min(1,dy)); invalidate(); sendDrive(); return true;
        }
        void reset() { x=0; y=0; invalidate(); sendDrive(); }
        private void sendDrive() { if(connection == null) return; int throttle=Math.round(-driveStick.y*100), steering=Math.round(steerStick.x*100); connection.send("D:"+throttle+":"+steering+":250\n"); }
    }

    @Override public void onBackPressed() { if (controlScreen) disconnectToBluetooth(); else super.onBackPressed(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); if (connection != null) connection.closeSilently(); unregisterReceiver(receiver); super.onDestroy(); }
}
