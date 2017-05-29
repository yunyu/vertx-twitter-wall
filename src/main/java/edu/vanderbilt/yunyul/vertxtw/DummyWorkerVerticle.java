package edu.vanderbilt.yunyul.vertxtw;

import io.vertx.core.AbstractVerticle;

public class DummyWorkerVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        while (true) {
            Thread.sleep(5000);
        }
    }
}