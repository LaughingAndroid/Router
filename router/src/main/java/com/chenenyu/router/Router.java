package com.chenenyu.router;

import android.net.Uri;
import android.util.Log;

import com.chenenyu.router.matcher.AbsMatcher;
import com.chenenyu.router.template.InterceptorTable;
import com.chenenyu.router.template.RouteTable;
import com.chenenyu.router.template.TargetInterceptors;
import com.chenenyu.router.util.RLog;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Entry class.
 * <p>
 * Created by chenenyu on 2016/12/20.
 */
public class Router {
    /**
     * You can get the raw uri in target page by call <code>intent.getStringExtra(Router.RAW_URI)</code>.
     */
    public static final String RAW_URI = "raw_uri";
    public static final String CALLBACK_ID = "router_call_back_id";

    private static List<RouteInterceptor> sGlobalInterceptors = new ArrayList<>();


    public static void initialize(Configuration configuration) {
        RLog.showLog(configuration.debuggable);
        AptHub.registerModules(configuration.modules);
    }

    public static IRouter build(String path) {
        return build(path == null ? null : Uri.parse(path));
    }

    public static IRouter build(Uri uri) {
        return new RealRouter().build(uri);
    }

    /**
     * Custom route table.
     */
    public static void handleRouteTable(RouteTable routeTable) {
        if (routeTable != null) {
            routeTable.handle(AptHub.routeTable);
        }
    }

    /**
     * Custom interceptor table.
     */
    public static void handleInterceptorTable(InterceptorTable interceptorTable) {
        if (interceptorTable != null) {
            interceptorTable.handle(AptHub.interceptorTable);
        }
    }

    /**
     * Custom targets' interceptors.
     */
    public static void handleTargetInterceptors(TargetInterceptors targetInterceptors) {
        if (targetInterceptors != null) {
            targetInterceptors.handle(AptHub.targetInterceptors);
        }
    }

    /**
     * Auto inject params from bundle.
     *
     * @param obj Instance of Activity or Fragment.
     */
    public static void injectParams(Object obj) {
        RealRouter.injectParams(obj);
    }

    /**
     * Global interceptor.
     */
    public static void addGlobalInterceptor(RouteInterceptor routeInterceptor) {
        sGlobalInterceptors.add(routeInterceptor);
    }

    public static List<RouteInterceptor> getGlobalInterceptors() {
        return sGlobalInterceptors;
    }

    /**
     * Register your own matcher.
     *
     * @see com.chenenyu.router.matcher.AbsExplicitMatcher
     * @see com.chenenyu.router.matcher.AbsImplicitMatcher
     */
    public static void registerMatcher(AbsMatcher matcher) {
        MatcherRegistry.register(matcher);
    }

    public static void clearMatcher() {
        MatcherRegistry.clear();
    }


    public static Map<Integer, RouteRequest> mRouteRequests = new Hashtable<>();

    public static void callback(int id, Object... objects) {
        if (mRouteRequests.containsKey(id)) {
            RouteRequest req = mRouteRequests.get(id);
            RouteCallback callback = req.getRouteCallback();
            if (callback != null) {
                callback.call(req, objects);
            }
        }
    }

    public static int addCallback(RouteRequest req) {
        mRouteRequests.put(req.getCallbackId(), req);
        return req.callbackId;
    }

    public static void remove(int id) {
        mRouteRequests.remove(id);
    }
}
