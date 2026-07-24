package app.grapheneos.gmscompat.lib.playintegrity;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.ext.PackageId;
import android.ext.integrity.IntegritySpoofPolicy;
import android.ext.settings.app.AswBlockPlayIntegrityApi;
import android.ext.settings.app.AswSpoofPlayIntegrity;
import android.os.IBinder;
import android.util.Log;

import java.util.function.UnaryOperator;

import app.grapheneos.gmscompat.lib.util.ServiceConnectionWrapper;

import static android.app.compat.gms.GmsCompat.appContext;

public class PlayIntegrityUtils {
    private static final String TAG = "PlayIntegrityUtils";

    public static ServiceConnection maybeReplaceServiceConnection(Intent service, ServiceConnection orig) {
        if (PackageId.PLAY_STORE_NAME.equals(service.getPackage())) {
            UnaryOperator<IBinder> binderOverride = null;

            final String CLASSIC_SERVICE =
                    "com.google.android.play.core.integrityservice.BIND_INTEGRITY_SERVICE";
            final String STANDARD_SERVICE =
                    "com.google.android.play.core.expressintegrityservice.BIND_EXPRESS_INTEGRITY_SERVICE";

            String action = service.getAction();
            if (STANDARD_SERVICE.equals(action)) {
                binderOverride = StandardPlayIntegrityServiceWrapper::new;
            } else if (CLASSIC_SERVICE.equals(action)) {
                binderOverride = ClassicPlayIntegrityServiceWrapper::new;
            }

            if (binderOverride != null) {
                return new ServiceConnectionWrapper(orig, binderOverride);
            }
        }
        return null;
    }

    static boolean isPlayIntegrityBlocked() {
        Context ctx = appContext();
        return AswBlockPlayIntegrityApi.I.get(ctx, ctx.getUserId(), ctx.getApplicationInfo(),
                GosPackageState.getForSelf(ctx));
    }

    /**
     * Calling package for PI wrappers is the client app process that GmsCompat is loaded into.
     */
    static String resolveCallingPackage() {
        Context ctx = appContext();
        return ctx != null ? ctx.getPackageName() : null;
    }

    /**
     * Spoof decision: block wins; otherwise true if the caller has SPOOF_PLAY_INTEGRITY or is
     * GMS/Vending while any other app has that flag (framework Global marker).
     */
    static boolean shouldSpoofPlayIntegrity() {
        if (isPlayIntegrityBlocked()) {
            return false;
        }
        Context ctx = appContext();
        if (ctx == null) {
            return false;
        }
        String pkg = resolveCallingPackage();
        if (pkg == null) {
            return false;
        }
        return IntegritySpoofPolicy.isPlayIntegritySpoofEnabled(ctx, pkg, ctx.getUserId());
    }

    static boolean hasExplicitSpoofFlag() {
        Context ctx = appContext();
        if (ctx == null) {
            return false;
        }
        ApplicationInfo ai = ctx.getApplicationInfo();
        return AswSpoofPlayIntegrity.I.get(ctx, ctx.getUserId(), ai, GosPackageState.getForSelf(ctx));
    }

    static void logSpoofDecision(boolean spoofing) {
        if (spoofing) {
            Log.d(TAG, "Play Integrity spoof active for " + resolveCallingPackage()
                    + " (props/keystore path; token request allowed through)");
        }
    }

    /**
     * Re-apply framework Build prop imitation before an integrity token request so hot-updated
     * props.json is visible even if the process started before the last Settings apply.
     */
    static void ensureSpoofProcessState() {
        if (!shouldSpoofPlayIntegrity()) {
            return;
        }
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        try {
            com.android.internal.util.integrity.IntegrityPropHooks.setProps(ctx);
        } catch (Throwable t) {
            Log.w(TAG, "IntegrityPropHooks.setProps failed", t);
        }
    }
}
