package com.lcg.match;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class Lib {

    static {
        Native.register(Lib.class, "match");
    }

    public static native Pointer model_new(String path);

    public static native void model_free(Pointer model);

    public static native Pointer recognizer_new(Model model, float sample_rate);

    public static native Pointer recognizer_new_grm(Pointer model, float sample_rate, String grammar);

    public static native int recognizer_match(Pointer recognizer, String factor, short[] data, int len);

    public static native void recognizer_reset(Pointer recognizer);

    public static native void recognizer_free(Pointer recognizer);
}
