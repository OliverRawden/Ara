package tech.rawden.ara.platform;

import tech.rawden.ara.util.OsType;

import javafx.stage.Stage;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.lang.reflect.Field;

public class MacWindow {

    private static final long NS_FULL_SIZE_CONTENT_VIEW = 32768L;
    private static final long NS_WINDOW_TITLE_HIDDEN = 1L;
    private static final long NS_TOOLBAR_STYLE_UNIFIED = 1L;
    private static final long NS_TITLEBAR_SEPARATOR_NONE = 1L;

    private static boolean applied = false;

    public static void applyModernStyle(Stage stage) {
        if (OsType.ofLocal() != OsType.MACOS || applied) return;

        try {
            applyNative(stage);
            applied = true;
        } catch (Exception e) {
            // Harmless on some JavaFX versions (internal API changed); modern macOS titlebar may not apply.
            if (!applied) {
                System.out.println("MacWindow: Modern style not available (JavaFX internal API) - " + e.getMessage());
            }
        }
    }

    private static void applyNative(Stage stage) throws Exception {
        var getPeer = Stage.class.getDeclaredMethod("getPeer");
        getPeer.setAccessible(true);
        var tkStage = getPeer.invoke(stage);
        if (tkStage == null) throw new RuntimeException("TKStage is null");

        Class<?> clazz = tkStage.getClass();
        Field pf = null;
        while (clazz != null && pf == null) {
            try {
                pf = clazz.getDeclaredField("platformWindow");
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        if (pf == null) throw new RuntimeException("platformWindow field not found");
        pf.setAccessible(true);

        var platformWindow = pf.get(tkStage);
        if (platformWindow == null) throw new RuntimeException("platformWindow is null");

        var getNativeWindow = platformWindow.getClass().getMethod("getNativeWindow");
        long nsWindowPtr = (long) getNativeWindow.invoke(platformWindow);
        if (nsWindowPtr == 0) throw new RuntimeException("native window handle is 0");

        configureNative(nsWindowPtr);
    }

    private static void configureNative(long nsWindowPtr) {
        var objc = NativeLibrary.getInstance("objc");
        var msgSend = objc.getFunction("objc_msgSend");
        var selReg = objc.getFunction("sel_registerName");

        var nsWindow = new Pointer(nsWindowPtr);

        // Make titlebar transparent — content extends behind it
        var setTransparentSel = selReg.invoke(Pointer.class, new Object[] {"setTitlebarAppearsTransparent:"});
        msgSend.invoke(Pointer.class, new Object[] {nsWindow, setTransparentSel, (byte) 1});

        // Hide the title text so it doesn't float over content
        var setTitleVisSel = selReg.invoke(Pointer.class, new Object[] {"setTitleVisibility:"});
        msgSend.invoke(Pointer.class, new Object[] {nsWindow, setTitleVisSel, NS_WINDOW_TITLE_HIDDEN});

        // Full-size content view — content fills entire window behind titlebar
        var styleMaskSel = selReg.invoke(Pointer.class, new Object[] {"styleMask"});
        long currentMask = (Long) msgSend.invoke(Long.class, new Object[] {nsWindow, styleMaskSel});
        long fullSizeMask = currentMask | NS_FULL_SIZE_CONTENT_VIEW;
        var setStyleMaskSel = selReg.invoke(Pointer.class, new Object[] {"setStyleMask:"});
        msgSend.invoke(Pointer.class, new Object[] {nsWindow, setStyleMaskSel, fullSizeMask});

        // Unified toolbar style — traffic lights with standard spacing
        var setToolbarSel = selReg.invoke(Pointer.class, new Object[] {"setToolbarStyle:"});
        msgSend.invoke(Pointer.class, new Object[] {nsWindow, setToolbarSel, NS_TOOLBAR_STYLE_UNIFIED});

        // Remove the separator line below the titlebar for a seamless look
        var setSepSel = selReg.invoke(Pointer.class, new Object[] {"setTitlebarSeparatorStyle:"});
        msgSend.invoke(Pointer.class, new Object[] {nsWindow, setSepSel, NS_TITLEBAR_SEPARATOR_NONE});

        // Allow window dragging by background (standard modern macOS behavior)
        var setMovableSel = selReg.invoke(Pointer.class, new Object[] {"setMovableByWindowBackground:"});
        msgSend.invoke(Pointer.class, new Object[] {nsWindow, setMovableSel, (byte) 1});
    }
}
