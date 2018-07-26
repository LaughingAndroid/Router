package com.chenenyu.router;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.chenenyu.router.matcher.AbsExplicitMatcher;
import com.chenenyu.router.matcher.AbsImplicitMatcher;
import com.chenenyu.router.matcher.AbsMatcher;
import com.chenenyu.router.template.ParamInjector;
import com.chenenyu.router.util.RLog;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core of router.
 * <br>
 * Created by chenenyu on 2017/3/30.
 */
class RealRouter extends AbsRouter {
    RealRouter() {
    }

    /**
     * Auto inject params from bundle.
     *
     * @param obj Activity or Fragment.
     */
    static void injectParams(Object obj) {
        if (obj instanceof Activity || obj instanceof Fragment || obj instanceof android.app.Fragment) {
            String key = obj.getClass().getCanonicalName();
            Class<ParamInjector> clz;
            if (!AptHub.injectors.containsKey(key)) {
                try {
                    //noinspection unchecked
                    clz = (Class<ParamInjector>) Class.forName(key + AptHub.PARAM_CLASS_SUFFIX);
                    AptHub.injectors.put(key, clz);
                } catch (ClassNotFoundException e) {
                    RLog.e("Inject params failed.", e);
                    return;
                }
            } else {
                clz = AptHub.injectors.get(key);
            }
            try {
                ParamInjector injector = clz.newInstance();
                injector.inject(obj);
            } catch (Exception e) {
                RLog.e("Inject params failed.", e);
            }
        } else {
            RLog.e("The obj you passed must be an instance of Activity or Fragment.");
        }
    }

    private void callback(RouteResult result, String msg) {
        if (result != RouteResult.SUCCEED) {
            RLog.w(msg);
        }
        if (result == RouteResult.SUCCEED && mRouteRequest.getRouteCallback() != null) {
            Router.addCallback(mRouteRequest);
        }
    }

    @Override
    public Object getFragment(Object source) {
        if (mRouteRequest.getUri() == null) {
            callback(RouteResult.FAILED, "uri == null.");
            return null;
        }

        Context context = null;
        if (source instanceof Context) {
            context = (Context) source;
        } else if (source instanceof Fragment) {
            context = ((Fragment) source).getContext();
        } else if (source instanceof android.app.Fragment) {
            if (Build.VERSION.SDK_INT >= 23) {
                context = ((android.app.Fragment) source).getContext();
            } else {
                context = ((android.app.Fragment) source).getActivity();
            }
        }
        if (context == null) {
            callback(RouteResult.FAILED, "Can't retrieve context from source.");
            return null;
        }

        if (!mRouteRequest.isSkipInterceptors()) {
            for (RouteInterceptor interceptor : Router.getGlobalInterceptors()) {
                if (interceptor.intercept(source, mRouteRequest)) {
                    callback(RouteResult.INTERCEPTED, String.format(
                            "Intercepted by global interceptor: %s.",
                            interceptor.getClass().getSimpleName()));
                    return null;
                }
            }
        }

        // Fragment只能匹配显式Matcher
        List<AbsExplicitMatcher> matcherList = MatcherRegistry.getExplicitMatcher();
        if (matcherList.isEmpty()) {
            callback(RouteResult.FAILED, "The MatcherRegistry contains no explicit matcher.");
            return null;
        }

        if (AptHub.routeTable.isEmpty()) {
            callback(RouteResult.FAILED, "The route table contains no mapping.");
            return null;
        }

        Set<Map.Entry<String, Class<?>>> entries = AptHub.routeTable.entrySet();

        for (AbsExplicitMatcher matcher : matcherList) {
            for (Map.Entry<String, Class<?>> entry : entries) {
                if (matcher.match(context, mRouteRequest.getUri(), entry.getKey(), mRouteRequest)) {
                    RLog.i("Caught by " + matcher.getClass().getCanonicalName());
                    if (intercept(source, assembleClassInterceptors(entry.getValue()))) {
                        return null;
                    }
                    Object result = matcher.generate(context, mRouteRequest.getUri(), entry.getValue(), false);
                    if (result instanceof Fragment) {
                        Fragment fragment = (Fragment) result;
                        Bundle bundle = mRouteRequest.getExtras();
                        if (bundle != null && !bundle.isEmpty()) {
                            fragment.setArguments(bundle);
                        }
                        return fragment;
                    } else if (result instanceof android.app.Fragment) {
                        android.app.Fragment fragment = (android.app.Fragment) result;
                        Bundle bundle = mRouteRequest.getExtras();
                        if (bundle != null && !bundle.isEmpty()) {
                            fragment.setArguments(bundle);
                        }
                        return fragment;
                    } else {
                        callback(RouteResult.FAILED, String.format(
                                "The matcher can't generate a fragment instance for uri: %s",
                                mRouteRequest.getUri().toString()));
                        return null;
                    }
                }
            }
        }

        callback(RouteResult.FAILED, String.format(
                "Can not find an Fragment that matches the given uri: %s", mRouteRequest.getUri()));
        return null;
    }

    @Override
    public Intent getIntent(Object source) {
        RLog.i("getIntent uri = " + mRouteRequest.getUri());
        if (mRouteRequest.getUri() == null) {
            callback(RouteResult.FAILED, "uri == null.");
            return null;
        }

        Context context = null;
        if (source instanceof Context) {
            context = (Context) source;
        } else if (source instanceof Fragment) {
            context = ((Fragment) source).getContext();
        } else if (source instanceof android.app.Fragment) {
            if (Build.VERSION.SDK_INT >= 23) {
                context = ((android.app.Fragment) source).getContext();
            } else {
                context = ((android.app.Fragment) source).getActivity();
            }
        }

        RLog.i("getIntent context = " + context);
        if (context == null) {
            callback(RouteResult.FAILED, "Can't retrieve context from source.");
            return null;
        }

        if (!mRouteRequest.isSkipInterceptors()) {
            for (RouteInterceptor interceptor : Router.getGlobalInterceptors()) {
                if (interceptor.intercept(source, mRouteRequest)) {
                    callback(RouteResult.INTERCEPTED, String.format(
                            "Intercepted by global interceptor: %s.",
                            interceptor.getClass().getSimpleName()));
                    return null;
                }
            }
        }

        List<AbsMatcher> matcherList = MatcherRegistry.getMatcher();
        RLog.i("getIntent matcherList = " + matcherList);

        if (matcherList.isEmpty()) {
            callback(RouteResult.FAILED, "The MatcherRegistry contains no matcher.");
            return null;
        }

        Set<Map.Entry<String, Class<?>>> entries = AptHub.routeTable.entrySet();

        for (AbsMatcher matcher : matcherList) {
            if (AptHub.routeTable.isEmpty()) { // implicit totally.
                if (matcher.match(context, mRouteRequest.getUri(), null, mRouteRequest)) {
                    RLog.i("Caught by " + matcher.getClass().getCanonicalName());
                    return generateIntent(source, context, matcher, null);
                }
            } else {
                boolean isImplicit = matcher instanceof AbsImplicitMatcher;
                for (Map.Entry<String, Class<?>> entry : entries) {
                    if (matcher.match(context, mRouteRequest.getUri(), isImplicit ? null : entry.getKey(), mRouteRequest)) {
                        RLog.i("Caught by " + matcher.getClass().getCanonicalName());
                        return generateIntent(source, context, matcher, isImplicit ? null : entry.getValue());
                    }
                }
            }
        }

        callback(RouteResult.FAILED, String.format(
                "Can not find an Activity that matches the given uri: %s", mRouteRequest.getUri()));
        return null;
    }

    /**
     * Do intercept and then generate intent by the given matcher, finally assemble extras.
     *
     * @param source  activity or fragment
     * @param context source context
     * @param matcher current matcher
     * @param target  route target
     * @return finally intent.
     */
    private Intent generateIntent(Object source, Context context, AbsMatcher matcher, @Nullable Class<?> target) {
        RLog.i("generateIntent intercept:" + target);
        // 1. intercept
        if (intercept(source, assembleClassInterceptors(target))) {
            return null;
        }

        // 2. generate
        Object result = matcher.generate(context, mRouteRequest.getUri(), target, true);
        RLog.i("generateIntent matcher.generate result:" + result);
        // 3. assemble
        if (result instanceof Intent) {
            Intent intent = (Intent) result;
            if (mRouteRequest.getExtras() != null && !mRouteRequest.getExtras().isEmpty()) {
                intent.putExtras(mRouteRequest.getExtras());
            }
            if (mRouteRequest.getFlags() != 0) {
                intent.addFlags(mRouteRequest.getFlags());
            }
            if (mRouteRequest.getData() != null) {
                intent.setData(mRouteRequest.getData());
            }
            if (mRouteRequest.getType() != null) {
                intent.setType(mRouteRequest.getType());
            }
            if (mRouteRequest.getAction() != null) {
                intent.setAction(mRouteRequest.getAction());
            }
            RLog.i("generateIntent return:" + result);
            return intent;
        } else {
            callback(RouteResult.FAILED, String.format(
                    "The matcher can't generate an intent for uri: %s",
                    mRouteRequest.getUri().toString()));
            return null;
        }
    }

    /**
     * Assemble final interceptors for class.
     *
     * @param target activity or fragment
     * @return Interceptors set.
     */
    private Set<String> assembleClassInterceptors(@Nullable Class<?> target) {
        if (mRouteRequest.isSkipInterceptors()) {
            return null;
        }
        // Assemble final interceptors
        Set<String> finalInterceptors = new LinkedHashSet<>();
        if (target != null) {
            // 1. Add original interceptors in Map
            String[] baseInterceptors = AptHub.targetInterceptors.get(target);
            if (baseInterceptors != null && baseInterceptors.length > 0) {
                Collections.addAll(finalInterceptors, baseInterceptors);
            }
            // 2. Skip temp removed interceptors
            if (mRouteRequest.getRemovedInterceptors() != null) {
                finalInterceptors.removeAll(mRouteRequest.getRemovedInterceptors());
            }
        }
        // 3. Add temp added interceptors
        if (mRouteRequest.getAddedInterceptors() != null) {
            finalInterceptors.addAll(mRouteRequest.getAddedInterceptors());
        }
        return finalInterceptors;
    }

    /**
     * Find interceptors
     *
     * @param source            activity or fragment instance.
     * @param finalInterceptors all interceptors
     * @return True if intercepted, false otherwise.
     */
    private boolean intercept(Object source, Set<String> finalInterceptors) {
        if (mRouteRequest.isSkipInterceptors()) {
            return false;
        }
        if (finalInterceptors != null && !finalInterceptors.isEmpty()) {
            for (String name : finalInterceptors) {
                RouteInterceptor interceptor = AptHub.interceptorInstances.get(name);
                if (interceptor == null) {
                    Class<? extends RouteInterceptor> clz = AptHub.interceptorTable.get(name);
                    try {
                        Constructor<? extends RouteInterceptor> constructor = clz.getConstructor();
                        interceptor = constructor.newInstance();
                        AptHub.interceptorInstances.put(name, interceptor);
                    } catch (Exception e) {
                        RLog.e("Can't construct a interceptor with name: " + name);
                        e.printStackTrace();
                    }
                }
                // do intercept
                if (interceptor != null && interceptor.intercept(source, mRouteRequest)) {
                    callback(RouteResult.INTERCEPTED, String.format(
                            "Intercepted: {uri: %s, interceptor: %s}",
                            mRouteRequest.getUri().toString(), name));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean go(Context context) {
        RLog.i("start create intent: ");
        Intent intent = getIntent(context);
        RLog.i("intent: " + intent);

        if (intent == null) {
            return false;
        }
        Bundle options = mRouteRequest.getActivityOptionsBundle();

        if (context instanceof Activity) {

            ActivityCompat.startActivityForResult((Activity) context, intent,
                    mRouteRequest.getRequestCode(), options);
            RLog.i("startActivityForResult: " + intent);

            if (mRouteRequest.getEnterAnim() >= 0 && mRouteRequest.getExitAnim() >= 0) {
                // Add transition animation.
                ((Activity) context).overridePendingTransition(
                        mRouteRequest.getEnterAnim(), mRouteRequest.getExitAnim());
            }
        } else {
            RLog.i("FLAG_ACTIVITY_NEW_TASK: " + intent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // The below api added in v4:25.1.0
            // ContextCompat.startActivity(context, intent, options);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                context.startActivity(intent, options);
            } else {
                context.startActivity(intent);
            }
        }

        callback(RouteResult.SUCCEED, null);
        return true;
    }
}
