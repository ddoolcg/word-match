package com.lcg.match;

import com.sun.jna.PointerType;

import java.io.IOException;

public class Model extends PointerType implements AutoCloseable {
    public Model() {
        super();
    }

    public Model(String path) throws IOException {
        super(Lib.model_new(path));

        if (getPointer() == null) {
            throw new IOException("Failed to create a model");
        }
    }

    @Override
    public void close() {
        Lib.model_free(this.getPointer());
    }
}
