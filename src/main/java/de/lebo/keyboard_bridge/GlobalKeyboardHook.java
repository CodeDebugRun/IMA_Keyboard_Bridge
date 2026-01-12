package de.lebo.keyboard_bridge;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;

import java.util.function.Consumer;

/**
 * Global Keyboard Hook using Windows Low-Level Keyboard Hook (WH_KEYBOARD_LL).
 * Captures all keyboard input system-wide, regardless of which window has focus.
 * Used to detect barcode scanner input without requiring application focus.
 */
public class GlobalKeyboardHook {

    // Windows API constants
    private static final int WH_KEYBOARD_LL = 13;   // Low-level keyboard hook type
    private static final int WM_KEYDOWN = 0x0100;   // Key pressed message

    // Hook management
    private HHOOK hook;                              // Windows hook handle
    private LowLevelKeyboardProc keyboardProc;       // Callback procedure
    private Thread hookThread;                       // Dedicated thread for message loop
    private volatile boolean running = false;        // Thread-safe running flag

    // Barcode buffering
    private final StringBuilder buffer = new StringBuilder();
    private final Consumer<String> onBarcodeScanned; // Callback when barcode is complete

    // Scanner detection: scanners type very fast (< 50ms between keys)
    // Human typing is slower (> 150ms between keys)
    private long lastKeyTime = 0;
    private static final long MAX_KEY_INTERVAL_MS = 100; // Threshold to detect scanner vs human

    /**
     * Creates a new GlobalKeyboardHook.
     * @param onBarcodeScanned Callback function invoked when a complete barcode is detected
     */
    public GlobalKeyboardHook(Consumer<String> onBarcodeScanned) {
        this.onBarcodeScanned = onBarcodeScanned;
    }

    /**
     * Starts the keyboard hook in a separate thread.
     * The hook runs a Windows message loop to receive keyboard events.
     */
    public void start() {
        if (running) return;
        running = true;

        hookThread = new Thread(() -> {
            // Define the callback that Windows will invoke for each key press
            keyboardProc = new LowLevelKeyboardProc() {
                @Override
                public LRESULT callback(int nCode, WPARAM wParam, KBDLLHOOKSTRUCT info) {
                    if (nCode >= 0 && wParam.intValue() == WM_KEYDOWN) {
                        int vkCode = info.vkCode;
                        long now = System.currentTimeMillis();

                        // If too much time passed, clear buffer (not a scanner)
                        if (now - lastKeyTime > MAX_KEY_INTERVAL_MS && buffer.length() > 0) {
                            buffer.setLength(0);
                        }
                        lastKeyTime = now;

                        // Enter key signals end of barcode
                        if (vkCode == 0x0D) {
                            if (buffer.length() > 0) {
                                String barcode = buffer.toString();
                                buffer.setLength(0);
                                onBarcodeScanned.accept(barcode);
                            }
                        } else {
                            // Append character to buffer
                            char c = vkCodeToChar(vkCode);
                            if (c != 0) {
                                buffer.append(c);
                            }
                        }
                    }
                    // Pass the event to the next hook in chain
                    return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam,
                            new LPARAM(Pointer.nativeValue(info.getPointer())));
                }
            };

            // Install the hook
            HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
            hook = User32.INSTANCE.SetWindowsHookEx(WH_KEYBOARD_LL, keyboardProc, hMod, 0);

            if (hook == null) {
                System.err.println("Failed to install keyboard hook!");
                return;
            }

            // Windows message loop - required for hook to receive events
            MSG msg = new MSG();
            while (running && User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        }, "KeyboardHookThread");

        hookThread.setDaemon(true);
        hookThread.start();
    }

    /**
     * Stops the keyboard hook and releases resources.
     */
    public void stop() {
        running = false;
        if (hook != null) {
            User32.INSTANCE.UnhookWindowsHookEx(hook);
            hook = null;
        }
    }

    /**
     * Converts Windows Virtual Key code to character.
     * Supports: 0-9, A-Z, numpad 0-9, minus, plus
     * @param vkCode Windows virtual key code
     * @return Character or 0 if not supported
     */
    private char vkCodeToChar(int vkCode) {
        // Numbers 0-9 (top row)
        if (vkCode >= 0x30 && vkCode <= 0x39) {
            return (char) vkCode;
        }
        // Numpad 0-9
        if (vkCode >= 0x60 && vkCode <= 0x69) {
            return (char) ('0' + (vkCode - 0x60));
        }
        // Letters A-Z (uppercase)
        if (vkCode >= 0x41 && vkCode <= 0x5A) {
            return (char) vkCode;
        }
        // Special characters
        if (vkCode == 0xBD) return '-'; // OEM_MINUS
        if (vkCode == 0xBB) return '+'; // OEM_PLUS

        return 0; // Unsupported key
    }
}
