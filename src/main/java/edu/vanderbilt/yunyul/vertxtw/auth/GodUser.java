package edu.vanderbilt.yunyul.vertxtw.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;

public class GodUser extends AbstractUser {
    private JsonObject principal;
    private String username;
    private PlaintextProvider provider;

    public GodUser(String username, PlaintextProvider provider) {
        this.username = username;
        this.provider = provider;
    }

    @Override
    protected void doIsPermitted(String permissionOrRole, Handler<AsyncResult<Boolean>> handler) {
        handler.handle(Future.succeededFuture(true));
    }

    @Override
    public JsonObject principal() {
        if (principal == null) {
            principal = new JsonObject().put("username", username);
        }
        return principal;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {
        if (authProvider instanceof PlaintextProvider) {
            this.provider = (PlaintextProvider) authProvider;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
