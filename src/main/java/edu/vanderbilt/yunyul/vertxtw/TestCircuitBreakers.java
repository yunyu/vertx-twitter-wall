package edu.vanderbilt.yunyul.vertxtw;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class TestCircuitBreakers {
    public TestCircuitBreakers(Vertx vertx) {
        CircuitBreaker alwaysFailBreaker = CircuitBreaker.create("twitter-legacy-api", vertx,
                new CircuitBreakerOptions().setMaxFailures(1).setResetTimeout(50000));
        vertx.setPeriodic(997, id -> alwaysFailBreaker.execute(future -> future.fail(new IllegalStateException())));

        CircuitBreaker occasionallyHalfOpenBreaker = CircuitBreaker.create("rng-api", vertx,
                new CircuitBreakerOptions().setMaxFailures(1).setResetTimeout(100));
        final boolean[] currState = {true};
        vertx.setPeriodic(7919, id -> occasionallyHalfOpenBreaker.execute(future -> {
            if (currState[0]) {
                future.complete();
            } else {
                future.fail(new IllegalStateException());
            }
            currState[0] = !currState[0];
        }));

        vertx.setPeriodic(5000, id -> {
            HttpClient httpClient = vertx.createHttpClient();
            httpClient.getNow("yunyul.in", "/resume.pdf", response -> {
                log("Received response with status code " + response.statusCode());
            });

        });
    }
}
