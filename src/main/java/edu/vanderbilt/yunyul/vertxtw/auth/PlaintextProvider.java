package edu.vanderbilt.yunyul.vertxtw.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/**
 * Very bad security do not actually use
 */
public class PlaintextProvider implements AuthProvider {
    private String username;
    private String password;

    public PlaintextProvider(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> handler) {
        if (authInfo.getString("username").equals(username) && authInfo.getString("password").equals(password)) {
            handler.handle(Future.succeededFuture(new GodUser(username, this)));
        } else {
            handler.handle(Future.failedFuture("Invalid username/password"));
        }
    }
}
