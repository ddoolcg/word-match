package com.lcg.match;

import com.sun.jna.PointerType;

import java.io.IOException;

public class Recognizer extends PointerType implements AutoCloseable {
    public Recognizer(Model model, float sampleRate) throws IOException {
        super(Lib.recognizer_new(model, sampleRate));

        if (getPointer() == null) {
            throw new IOException("Failed to create a recognizer");
        }
    }

    public Recognizer(Model model, float sampleRate, String grammar) throws IOException {
        super(Lib.recognizer_new_grm(model.getPointer(), sampleRate, grammar));

        if (getPointer() == null) {
            throw new IOException("Failed to create a recognizer");
        }
    }

    public int match(String factor, short[] data, int len) {
        return Lib.recognizer_match(this.getPointer(), factor, data, len);
    }

    public void reset() {
        Lib.recognizer_reset(this.getPointer());
    }

    @Override
    public void close() {
        Lib.recognizer_free(this.getPointer());
    }
}
