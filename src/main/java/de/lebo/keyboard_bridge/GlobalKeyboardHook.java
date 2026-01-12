package de.lebo.keyboard_bridge;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;

import java.util.function.Consumer;

public class GlobalKeyboardHook {

    private static final int WH_KEYBOARD_LL = 13;
    private static final int WM_KEYDOWN = 0x0100;

    private HHOOK hook;
    private LowLevelKeyboardProc keyboardProc;
    private Thread hookThread;
    private volatile boolean running = false;

    private final StringBuilder buffer = new StringBuilder();
    private final Consumer<String> onBarcodeScanned;

    //Barcode okuyucular hizli yazar <50ms
    private long lastKeyTime = 0;
    private static final long MAX_KEY_INTERVAL_MS = 100;

    public GlobalKeyboardHook(Consumer<String> onBarcodeScanned) {
        this.onBarcodeScanned = onBarcodeScanned;
    }

    public void start() {
        if (running) return;
        running = true;

        hookThread = new Thread(() -> {
            keyboardProc = new LowLevelKeyboardProc() {
                @Override
                public LRESULT callback(int nCode, WPARAM wParam, KBDLLHOOKSTRUCT info) {
                    if (nCode >= 0 && wParam.intValue() == WM_KEYDOWN) {
                        int vkCode = info.vkCode;
                        long now = System.currentTimeMillis();

                        // Cok uzun sure gectiyse buffer'i temizle (manuel yazim degil scanner)
                        if (now - lastKeyTime > MAX_KEY_INTERVAL_MS && buffer.length() > 0) {
                            buffer.setLength(0);
                        }
                        lastKeyTime = now;

                        if (vkCode == 0x0D) { // Enter
                            if (buffer.length() > 0) {
                                String barcode = buffer.toString();
                                buffer.setLength(0);
                                onBarcodeScanned.accept(barcode);
                            }
                        } else {
                            char c = vkCodeToChar(vkCode);
                            if (c != 0) {
                                buffer.append(c);
                            }
                        }
                    }
                    return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, new LPARAM(Pointer.nativeValue(info.getPointer())));
                }
            };

            HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
            hook = User32.INSTANCE.SetWindowsHookEx(WH_KEYBOARD_LL, keyboardProc, hMod, 0);

            if (hook == null) {
                System.err.println("Failed to install keyboard hook!");
                return;
            }

            MSG msg = new MSG();
            while (running && User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        }, "KeyboardHookThread");

        hookThread.setDaemon(true);
        hookThread.start();
    }

    public void stop() {
        running = false;
        if (hook != null) {
            User32.INSTANCE.UnhookWindowsHookEx(hook);
            hook = null;
        }
    }

    private char vkCodeToChar(int vkCode) {
        // Sayilar 0-9
        if (vkCode >= 0x30 && vkCode <= 0x39) {
            return (char) vkCode;
        }
        // Numpad 0-9
        if (vkCode >= 0x60 && vkCode <= 0x69) {
            return (char) ('0' + (vkCode - 0x60));
        }
        // Harfler A-Z
        if (vkCode >= 0x41 && vkCode <= 0x5A) {
            return (char) vkCode; // Buyuk harf
        }
        // Tire ve alt cizgi icin
        if (vkCode == 0xBD) return '-'; // OEM_MINUS
        if (vkCode == 0xBB) return '+'; // OEM_PLUS

        return 0; // Desteklenmeyen tus
    }
}

