/*
 * Created by wangzhuozhou on 2015/08/01.
 * Copyright 2015－2021 Sensors Data Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xjdata.analytics.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import com.sensorsdata.analytics.android.sdk.SensorsDataAutoTrackHelper;
import com.xjdata.analytics.android.sdk.data.DbAdapter;
import com.xjdata.analytics.android.sdk.data.DbParams;
import com.xjdata.analytics.android.sdk.deeplink.XJDataDeepLinkCallback;
import com.xjdata.analytics.android.sdk.listener.XJEventListener;
import com.xjdata.analytics.android.sdk.remote.BaseXJDataSDKRemoteManager;
import com.xjdata.analytics.android.sdk.util.AopUtil;
import com.xjdata.analytics.android.sdk.util.ChannelUtils;
import com.xjdata.analytics.android.sdk.util.JSONUtils;
import com.xjdata.analytics.android.sdk.util.NetworkUtils;
import com.xjdata.analytics.android.sdk.util.OaidHelper;
import com.xjdata.analytics.android.sdk.util.XJDataUtils;
import com.xjdata.analytics.android.sdk.util.TimeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXEVENT_TYPE;
import static com.xjdata.analytics.android.sdk.util.Base64Coder.CHARSET_UTF8;
import static com.xjdata.analytics.android.sdk.util.XJDataHelper.assertKey;
import static com.xjdata.analytics.android.sdk.util.XJDataHelper.assertPropertyTypes;
import static com.xjdata.analytics.android.sdk.util.XJDataHelper.assertValue;

/**
 * Sensors Analytics SDK
 */
public class XJDataAPI extends AbstractXJDataAPI {
    // 可视化埋点功能最低 API 版本
    public static final int VTRACK_SUPPORTED_MIN_API = 16;
    // SDK 版本，此属性插件会进行访问，谨慎修改
    static final String VERSION = BuildConfig.SDK_VERSION;
    // 此属性插件会进行访问，谨慎删除。当前 SDK 版本所需插件最低版本号，设为空，意为没有任何限制
    static final String MIN_PLUGIN_VERSION = BuildConfig.MIN_PLUGIN_VERSION;
    /**
     * 插件版本号，插件会用到此属性，请谨慎修改
     */
    static String ANDROID_PLUGIN_VERSION = "";

    //private
    XJDataAPI() {
        super();
    }

    XJDataAPI(Context context, String serverURL, DebugMode debugMode) {
        super(context, serverURL, debugMode);
    }

    /**
     * 获取 SensorsDataAPI 单例
     *
     * @param context App的Context
     * @return SensorsDataAPI 单例
     */
    public static XJDataAPI sharedInstance(Context context) {
        if (isSDKDisabled()) {
            return new XJDataAPIEmptyImplementation();
        }

        if (null == context) {
            return new XJDataAPIEmptyImplementation();
        }

        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();
            XJDataAPI instance = sInstanceMap.get(appContext);

            if (null == instance) {
                XJLog.i(TAG, "The static method sharedInstance(context, serverURL, debugMode) should be called before calling sharedInstance()");
                return new XJDataAPIEmptyImplementation();
            }
            return instance;
        }
    }

    /**
     * 初始化并获取 SensorsDataAPI 单例
     *
     * @param context App 的 Context
     * @param serverURL 用于收集事件的服务地址
     * @param debugMode Debug 模式,
     * {@link XJDataAPI.DebugMode}
     * @return SensorsDataAPI 单例
     */
    @Deprecated
    public static XJDataAPI sharedInstance(Context context, String serverURL, DebugMode debugMode) {
        return getInstance(context, serverURL, debugMode);
    }

    /**
     * 初始化并获取 SensorsDataAPI 单例
     *
     * @param context App 的 Context
     * @param serverURL 用于收集事件的服务地址
     * @return SensorsDataAPI 单例
     */
    @Deprecated
    public static XJDataAPI sharedInstance(Context context, String serverURL) {
        return getInstance(context, serverURL, DebugMode.DEBUG_OFF);
    }

    /**
     * 初始化并获取 SensorsDataAPI 单例
     *
     * @param context App 的 Context
     * @param saConfigOptions SDK 的配置项
     * @return SensorsDataAPI 单例
     */
    @Deprecated
    public static XJDataAPI sharedInstance(Context context, XJConfigOptions saConfigOptions) {
        mXJConfigOptions = saConfigOptions;
        XJDataAPI sensorsDataAPI = getInstance(context, saConfigOptions.mServerUrl, DebugMode.DEBUG_OFF);
        if (!sensorsDataAPI.mSDKConfigInit) {
            sensorsDataAPI.applySAConfigOptions();
        }
        return sensorsDataAPI;
    }

    /**
     * 初始化神策 SDK
     *
     * @param context App 的 Context
     * @param xjConfigOptions SDK 的配置项
     */
    public static void startWithConfigOptions(Context context, XJConfigOptions xjConfigOptions, String appKey) {
        if (context == null || xjConfigOptions == null||appKey==null||"".equals(appKey)) {
            throw new NullPointerException("Context、XJConfigOptions 、appKey不可以为 null");
        }
        xjConfigOptions.mAppKey=appKey;
        mXJConfigOptions = xjConfigOptions;
        XJDataAPI sensorsDataAPI = getInstance(context, xjConfigOptions.mServerUrl, DebugMode.DEBUG_OFF);
        if (!sensorsDataAPI.mSDKConfigInit) {
            sensorsDataAPI.applySAConfigOptions();
        }
    }


    private static XJDataAPI getInstance(Context context, String serverURL, DebugMode debugMode) {
        if (null == context) {
            return new XJDataAPIEmptyImplementation();
        }

        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();

            XJDataAPI instance = sInstanceMap.get(appContext);
            if (null == instance) {
                instance = new XJDataAPI(appContext, serverURL, debugMode);
                sInstanceMap.put(appContext, instance);
            }

            return instance;
        }
    }

    public static XJDataAPI sharedInstance() {
        if (isSDKDisabled()) {
            return new XJDataAPIEmptyImplementation();
        }

        synchronized (sInstanceMap) {
            if (sInstanceMap.size() > 0) {
                Iterator<XJDataAPI> iterator = sInstanceMap.values().iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            }
            return new XJDataAPIEmptyImplementation();
        }
    }

    /**
     * 返回预置属性
     *
     * @return JSONObject 预置属性
     */
    @Override
    public JSONObject getPresetProperties() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("$app_version", mDeviceInfo.get("$app_version"));
            properties.put("$lib", "Android");
            properties.put("$lib_version", VERSION);
            properties.put("$manufacturer", mDeviceInfo.get("$manufacturer"));
            properties.put("$model", mDeviceInfo.get("$model"));
            properties.put("$os", "Android");
            properties.put("$os_version", mDeviceInfo.get("$os_version"));
            properties.put("$screen_height", mDeviceInfo.get("$screen_height"));
            properties.put("$screen_width", mDeviceInfo.get("$screen_width"));
            String networkType = NetworkUtils.networkType(mContext);
            properties.put("$wifi", "WIFI".equals(networkType));
            properties.put("$network_type", networkType);
            properties.put("$carrier", mDeviceInfo.get("$carrier"));
            properties.put("$is_first_day", isFirstDay(System.currentTimeMillis()));
            properties.put("$app_id", mDeviceInfo.get("$app_id"));
            properties.put("$timezone_offset", mDeviceInfo.get("$timezone_offset"));
            if (mDeviceInfo.containsKey("$device_id")) {
                properties.put("$device_id", mDeviceInfo.get("$device_id"));
            }
            properties.put("$app_name", mDeviceInfo.get("$app_name"));
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return properties;
    }

    @Override
    public void enableLog(boolean enable) {
        XJLog.setEnableLog(enable);
    }

    @Override
    public long getMaxCacheSize() {
        return mXJConfigOptions.mMaxCacheSize;
    }

    @Override
    public void setMaxCacheSize(long maxCacheSize) {
        mXJConfigOptions.setMaxCacheSize(maxCacheSize);
    }

    @Override
    public void setFlushNetworkPolicy(int networkType) {
        mXJConfigOptions.setNetworkTypePolicy(networkType);
    }

    int getFlushNetworkPolicy() {
        return mXJConfigOptions.mNetworkTypePolicy;
    }

    @Override
    public int getFlushInterval() {
        return mXJConfigOptions.mFlushInterval;
    }

    @Override
    public void setFlushInterval(int flushInterval) {
        mXJConfigOptions.setFlushInterval(flushInterval);
    }

    @Override
    public int getFlushBulkSize() {
        return mXJConfigOptions.mFlushBulkSize;
    }

    @Override
    public void setFlushBulkSize(int flushBulkSize) {
        if (flushBulkSize < 0) {
            XJLog.i(TAG, "The value of flushBulkSize is invalid");
        }
        mXJConfigOptions.setFlushBulkSize(flushBulkSize);
    }

    @Override
    public int getSessionIntervalTime() {
        return mSessionTime;
    }

    @Override
    public void setSessionIntervalTime(int sessionIntervalTime) {
        if (DbAdapter.getInstance() == null) {
            XJLog.i(TAG, "The static method sharedInstance(context, serverURL, debugMode) should be called before calling sharedInstance()");
            return;
        }

        if (sessionIntervalTime < 10 * 1000 || sessionIntervalTime > 5 * 60 * 1000) {
            XJLog.i(TAG, "SessionIntervalTime:" + sessionIntervalTime + " is invalid, session interval time is between 10s and 300s.");
            return;
        }
        if (sessionIntervalTime != mSessionTime) {
            mSessionTime = sessionIntervalTime;
            DbAdapter.getInstance().commitSessionIntervalTime(sessionIntervalTime);
        }
    }

    @Override
    public void setGPSLocation(double latitude, double longitude) {
        try {
            if (mGPSLocation == null) {
                mGPSLocation = new XJDataGPSLocation();
            }

            mGPSLocation.setLatitude((long) (latitude * Math.pow(10, 6)));
            mGPSLocation.setLongitude((long) (longitude * Math.pow(10, 6)));
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void setCity(String city) {
        try {
            if (mGPSLocation == null) {
                mGPSLocation = new XJDataGPSLocation();
            }
            mGPSLocation.setCity(city);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }
    @Override
    public void setProvince(String province) {
        try {
            if (mGPSLocation == null) {
                mGPSLocation = new XJDataGPSLocation();
            }
            mGPSLocation.setProvince(province);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void setDistrict(String district) {
        try {
            if (mGPSLocation == null) {
                mGPSLocation = new XJDataGPSLocation();
            }
            mGPSLocation.setDistrict(district);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }
    @Override
    public void setCountry(String country) {
        try {
            if (mGPSLocation == null) {
                mGPSLocation = new XJDataGPSLocation();
            }
            mGPSLocation.setCountry(country);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }


    @Override
    public void clearGPSLocation() {
        mGPSLocation = null;
    }

    @Override
    public void enableTrackScreenOrientation(boolean enable) {
        try {
            if (enable) {
                if (mOrientationDetector == null) {
                    mOrientationDetector = new XJDataScreenOrientationDetector(mContext, SensorManager.SENSOR_DELAY_NORMAL);
                }
                mOrientationDetector.enable();
            } else {
                if (mOrientationDetector != null) {
                    mOrientationDetector.disable();
                    mOrientationDetector = null;
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void resumeTrackScreenOrientation() {
        try {
            if (mOrientationDetector != null) {
                mOrientationDetector.enable();
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void stopTrackScreenOrientation() {
        try {
            if (mOrientationDetector != null) {
                mOrientationDetector.disable();
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public String getScreenOrientation() {
        try {
            if (mOrientationDetector != null) {
                return mOrientationDetector.getOrientation();
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return null;
    }

    @Override
    public void setCookie(String cookie, boolean encode) {
        try {
            if (encode) {
                this.mCookie = URLEncoder.encode(cookie, CHARSET_UTF8);
            } else {
                this.mCookie = cookie;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public String getCookie(boolean decode) {
        try {
            if (decode) {
                return URLDecoder.decode(this.mCookie, CHARSET_UTF8);
            } else {
                return this.mCookie;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
            return null;
        }

    }

    @Deprecated
    @Override
    public void enableAutoTrack() {
        List<AutoTrackEventType> eventTypeList = new ArrayList<>();
        eventTypeList.add(AutoTrackEventType.APP_START);
        eventTypeList.add(AutoTrackEventType.APP_END);
        eventTypeList.add(AutoTrackEventType.APP_VIEW_SCREEN);
        enableAutoTrack(eventTypeList);
    }

    @Override
    public void enableAutoTrack(List<AutoTrackEventType> eventTypeList) {
        try {
            if (eventTypeList == null || eventTypeList.isEmpty()) {
                return;
            }
            this.mAutoTrack = true;
            for (AutoTrackEventType autoTrackEventType : eventTypeList) {
                mXJConfigOptions.setAutoTrackEventType(mXJConfigOptions.mAutoTrackEventType | autoTrackEventType.eventValue);
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void disableAutoTrack(List<AutoTrackEventType> eventTypeList) {
        ignoreAutoTrackEventType(eventTypeList);
    }

    @Override
    public void disableAutoTrack(AutoTrackEventType autoTrackEventType) {
        ignoreAutoTrackEventType(autoTrackEventType);
    }

    @Override
    public void trackAppCrash() {
        XJDataExceptionHandler.enableAppCrash();
    }

    @Override
    public boolean isAutoTrackEnabled() {
        if (isSDKDisabled()) {
            return false;
        }

        if (mRemoteManager != null) {
            Boolean isAutoTrackEnabled = mRemoteManager.isAutoTrackEnabled();
            if (isAutoTrackEnabled != null) {
                return isAutoTrackEnabled;
            }
        }
        return mAutoTrack;
    }

    @Override
    public void trackFragmentAppViewScreen() {
        mFragmentAPI.trackFragmentAppViewScreen();
    }

    @Override
    public boolean isTrackFragmentAppViewScreenEnabled() {
        return mFragmentAPI.isTrackFragmentAppViewScreenEnabled();
    }

    @Override
    public void enableReactNativeAutoTrack() {
        mXJConfigOptions.enableReactNativeAutoTrack(true);
    }

    @Override
    public boolean isReactNativeAutoTrackEnabled() {
        return mXJConfigOptions.mRNAutoTrackEnabled;
    }

    @Override
    public void showUpWebView(WebView webView, boolean isSupportJellyBean) {
        showUpWebView(webView, isSupportJellyBean, null);
    }

    @Override
    public void showUpWebView(WebView webView, boolean isSupportJellyBean, boolean enableVerify) {
        showUpWebView(webView, null, isSupportJellyBean, enableVerify);
    }

    @Override
    @Deprecated
    public void showUpWebView(WebView webView, JSONObject properties, boolean isSupportJellyBean, boolean enableVerify) {
        if (Build.VERSION.SDK_INT < 17 && !isSupportJellyBean) {
            XJLog.d(TAG, "For applications targeted to API level JELLY_BEAN or below, this feature NOT SUPPORTED");
            return;
        }

        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.addJavascriptInterface(new AppWebViewInterface(mContext, properties, enableVerify), "SensorsData_APP_JS_Bridge");
            SensorsDataAutoTrackHelper.addWebViewVisualInterface(webView);
        }
    }

    @Override
    @Deprecated
    public void showUpWebView(WebView webView, boolean isSupportJellyBean, JSONObject properties) {
        showUpWebView(webView, properties, isSupportJellyBean, false);
    }

    @Override
    @Deprecated
    public void showUpX5WebView(Object x5WebView, JSONObject properties, boolean isSupportJellyBean, boolean enableVerify) {
        try {
            if (Build.VERSION.SDK_INT < 17 && !isSupportJellyBean) {
                XJLog.d(TAG, "For applications targeted to API level JELLY_BEAN or below, this feature NOT SUPPORTED");
                return;
            }

            if (x5WebView == null) {
                return;
            }

            Class<?> clazz = x5WebView.getClass();
            Method addJavascriptInterface = clazz.getMethod("addJavascriptInterface", Object.class, String.class);
            if (addJavascriptInterface == null) {
                return;
            }
            addJavascriptInterface.invoke(x5WebView, new AppWebViewInterface(mContext, properties, enableVerify), "SensorsData_APP_JS_Bridge");
            SensorsDataAutoTrackHelper.addWebViewVisualInterface((View) x5WebView);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void showUpX5WebView(Object x5WebView, boolean enableVerify) {
        try {
            if (x5WebView == null) {
                return;
            }

            Class<?> clazz = x5WebView.getClass();
            Method addJavascriptInterface = clazz.getMethod("addJavascriptInterface", Object.class, String.class);
            if (addJavascriptInterface == null) {
                return;
            }
            addJavascriptInterface.invoke(x5WebView, new AppWebViewInterface(mContext, null, enableVerify), "SensorsData_APP_JS_Bridge");
            SensorsDataAutoTrackHelper.addWebViewVisualInterface((View) x5WebView);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void showUpX5WebView(Object x5WebView) {
        showUpX5WebView(x5WebView, false);
    }

    @Override
    public void ignoreAutoTrackActivities(List<Class<?>> activitiesList) {
        if (activitiesList == null || activitiesList.size() == 0) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        int hashCode;
        for (Class<?> activity : activitiesList) {
            if (activity != null) {
                hashCode = activity.hashCode();
                if (!mAutoTrackIgnoredActivities.contains(hashCode)) {
                    mAutoTrackIgnoredActivities.add(hashCode);
                }
            }
        }
    }

    @Override
    public void resumeAutoTrackActivities(List<Class<?>> activitiesList) {
        if (activitiesList == null || activitiesList.size() == 0) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        try {
            int hashCode;
            for (Class activity : activitiesList) {
                if (activity != null) {
                    hashCode = activity.hashCode();
                    if (mAutoTrackIgnoredActivities.contains(hashCode)) {
                        mAutoTrackIgnoredActivities.remove(Integer.valueOf(hashCode));
                    }
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void ignoreAutoTrackActivity(Class<?> activity) {
        if (activity == null) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        try {
            int hashCode = activity.hashCode();
            if (!mAutoTrackIgnoredActivities.contains(hashCode)) {
                mAutoTrackIgnoredActivities.add(hashCode);
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void resumeAutoTrackActivity(Class<?> activity) {
        if (activity == null) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        try {
            int hashCode = activity.hashCode();
            if (mAutoTrackIgnoredActivities.contains(hashCode)) {
                mAutoTrackIgnoredActivities.remove(Integer.valueOf(hashCode));
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void enableAutoTrackFragment(Class<?> fragment) {
        mFragmentAPI.enableAutoTrackFragment(fragment);
    }

    @Override
    public void enableAutoTrackFragments(List<Class<?>> fragmentsList) {
        mFragmentAPI.enableAutoTrackFragments(fragmentsList);
    }

    @Override
    public boolean isActivityAutoTrackAppViewScreenIgnored(Class<?> activity) {
        if (activity == null) {
            return false;
        }
        if (mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        if (activity.getAnnotation(XJDataIgnoreTrackAppViewScreenAndAppClick.class) != null) {
            return true;
        }

        return activity.getAnnotation(XJDataIgnoreTrackAppViewScreen.class) != null;
    }

    @Override
    public boolean isFragmentAutoTrackAppViewScreen(Class<?> fragment) {
        return mFragmentAPI.isFragmentAutoTrackAppViewScreen(fragment);
    }

    @Override
    public void ignoreAutoTrackFragments(List<Class<?>> fragmentList) {
        mFragmentAPI.ignoreAutoTrackFragments(fragmentList);
    }

    @Override
    public void ignoreAutoTrackFragment(Class<?> fragment) {
        mFragmentAPI.ignoreAutoTrackFragment(fragment);
    }

    @Override
    public void resumeIgnoredAutoTrackFragments(List<Class<?>> fragmentList) {
        mFragmentAPI.resumeIgnoredAutoTrackFragments(fragmentList);
    }

    @Override
    public void resumeIgnoredAutoTrackFragment(Class<?> fragment) {
        mFragmentAPI.resumeIgnoredAutoTrackFragment(fragment);
    }

    @Override
    public boolean isActivityAutoTrackAppClickIgnored(Class<?> activity) {
        if (activity == null) {
            return false;
        }
        if (mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        if (activity.getAnnotation(XJDataIgnoreTrackAppViewScreenAndAppClick.class) != null) {
            return true;
        }

        return activity.getAnnotation(XJDataIgnoreTrackAppClick.class) != null;

    }

    @Deprecated
    @Override
    public void ignoreAutoTrackEventType(AutoTrackEventType autoTrackEventType) {
        if (autoTrackEventType == null) {
            return;
        }

        if (mXJConfigOptions.mAutoTrackEventType == 0) {
            return;
        }

        int union = mXJConfigOptions.mAutoTrackEventType | autoTrackEventType.eventValue;
        if (union == autoTrackEventType.eventValue) {
            mXJConfigOptions.setAutoTrackEventType(0);
        } else {
            mXJConfigOptions.setAutoTrackEventType(autoTrackEventType.eventValue ^ union);
        }

        if (mXJConfigOptions.mAutoTrackEventType == 0) {
            this.mAutoTrack = false;
        }
    }

    @Deprecated
    @Override
    public void ignoreAutoTrackEventType(List<AutoTrackEventType> eventTypeList) {
        if (eventTypeList == null) {
            return;
        }

        if (mXJConfigOptions.mAutoTrackEventType == 0) {
            return;
        }

        for (AutoTrackEventType autoTrackEventType : eventTypeList) {
            if ((mXJConfigOptions.mAutoTrackEventType | autoTrackEventType.eventValue) == mXJConfigOptions.mAutoTrackEventType) {
                mXJConfigOptions.setAutoTrackEventType(mXJConfigOptions.mAutoTrackEventType ^ autoTrackEventType.eventValue);
            }
        }

        if (mXJConfigOptions.mAutoTrackEventType == 0) {
            this.mAutoTrack = false;
        }
    }

    @Override
    public boolean isAutoTrackEventTypeIgnored(AutoTrackEventType eventType) {
        if (eventType == null) {
            return false;
        }
        return isAutoTrackEventTypeIgnored(eventType.eventValue);
    }

    @Override
    public boolean isAutoTrackEventTypeIgnored(int autoTrackEventType) {
        if (mRemoteManager != null) {
            Boolean isIgnored = mRemoteManager.isAutoTrackEventTypeIgnored(autoTrackEventType);
            if (isIgnored != null) {
                if (isIgnored) {
                    XJLog.i(TAG, "remote config: " + AutoTrackEventType.autoTrackEventName(autoTrackEventType) + " is ignored by remote config");
                }
                return isIgnored;
            }
        }

        return (mXJConfigOptions.mAutoTrackEventType | autoTrackEventType) != mXJConfigOptions.mAutoTrackEventType;
    }

    @Override
    public void setViewID(View view, String viewID) {
        if (view != null && !TextUtils.isEmpty(viewID)) {
            view.setTag(R.id.sensors_analytics_tag_view_id, viewID);
        }
    }

    @Override
    public void setViewID(android.app.Dialog view, String viewID) {
        try {
            if (view != null && !TextUtils.isEmpty(viewID)) {
                if (view.getWindow() != null) {
                    view.getWindow().getDecorView().setTag(R.id.sensors_analytics_tag_view_id, viewID);
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void setViewID(Object alertDialog, String viewID) {
        try {
            if (alertDialog == null) {
                return;

            }

            Class<?> supportAlertDialogClass = null;
            Class<?> androidXAlertDialogClass = null;
            Class<?> currentAlertDialogClass;
            try {
                supportAlertDialogClass = Class.forName("android.support.v7.app.AlertDialog");
            } catch (Exception e) {
                //ignored
            }

            try {
                androidXAlertDialogClass = Class.forName("androidx.appcompat.app.AlertDialog");
            } catch (Exception e) {
                //ignored
            }

            if (supportAlertDialogClass != null) {
                currentAlertDialogClass = supportAlertDialogClass;
            } else {
                currentAlertDialogClass = androidXAlertDialogClass;
            }

            if (currentAlertDialogClass == null) {
                return;
            }

            if (!currentAlertDialogClass.isInstance(alertDialog)) {
                return;
            }

            if (!TextUtils.isEmpty(viewID)) {
                Method getWindowMethod = alertDialog.getClass().getMethod("getWindow");
                if (getWindowMethod == null) {
                    return;
                }

                Window window = (Window) getWindowMethod.invoke(alertDialog);
                if (window != null) {
                    window.getDecorView().setTag(R.id.sensors_analytics_tag_view_id, viewID);
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void setViewActivity(View view, Activity activity) {
        try {
            if (view == null || activity == null) {
                return;
            }
            view.setTag(R.id.sensors_analytics_tag_view_activity, activity);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void setViewFragmentName(View view, String fragmentName) {
        try {
            if (view == null || TextUtils.isEmpty(fragmentName)) {
                return;
            }
            view.setTag(R.id.sensors_analytics_tag_view_fragment_name2, fragmentName);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void ignoreView(View view) {
        if (view != null) {
            view.setTag(R.id.sensors_analytics_tag_view_ignored, "1");
        }
    }

    @Override
    public void ignoreView(View view, boolean ignore) {
        if (view != null) {
            view.setTag(R.id.sensors_analytics_tag_view_ignored, ignore ? "1" : "0");
        }
    }

    @Override
    public void setViewProperties(View view, JSONObject properties) {
        if (view == null || properties == null) {
            return;
        }

        view.setTag(R.id.sensors_analytics_tag_view_properties, properties);
    }

    @Override
    public List<Class> getIgnoredViewTypeList() {
        if (mIgnoredViewTypeList == null) {
            mIgnoredViewTypeList = new ArrayList<>();
        }

        return mIgnoredViewTypeList;
    }

    @Override
    public void ignoreViewType(Class viewType) {
        if (viewType == null) {
            return;
        }

        if (mIgnoredViewTypeList == null) {
            mIgnoredViewTypeList = new ArrayList<>();
        }

        if (!mIgnoredViewTypeList.contains(viewType)) {
            mIgnoredViewTypeList.add(viewType);
        }
    }

    @Override
    public boolean isVisualizedAutoTrackActivity(Class<?> activity) {
        try {
            if (activity == null) {
                return false;
            }
            if (mVisualizedAutoTrackActivities.size() == 0) {
                return true;
            }
            if (mVisualizedAutoTrackActivities.contains(activity.hashCode())) {
                return true;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return false;
    }

    @Override
    public void addVisualizedAutoTrackActivity(Class<?> activity) {
        try {
            if (activity == null) {
                return;
            }
            mVisualizedAutoTrackActivities.add(activity.hashCode());
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void addVisualizedAutoTrackActivities(List<Class<?>> activitiesList) {
        try {
            if (activitiesList == null || activitiesList.size() == 0) {
                return;
            }

            for (Class<?> activity : activitiesList) {
                if (activity != null) {
                    int hashCode = activity.hashCode();
                    if (!mVisualizedAutoTrackActivities.contains(hashCode)) {
                        mVisualizedAutoTrackActivities.add(hashCode);
                    }
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public boolean isVisualizedAutoTrackEnabled() {
        return mXJConfigOptions.mVisualizedEnabled;
    }

    @Override
    public void enableVisualizedAutoTrackConfirmDialog(boolean enable) {
        mXJConfigOptions.enableVisualizedAutoTrackConfirmDialog(enable);
    }

    @Override
    public void enableVisualizedAutoTrack() {
        mXJConfigOptions.enableVisualizedAutoTrack(true);
    }

    @Override
    public boolean isHeatMapActivity(Class<?> activity) {
        try {
            if (activity == null) {
                return false;
            }
            if (mHeatMapActivities.size() == 0) {
                return true;
            }
            if (mHeatMapActivities.contains(activity.hashCode())) {
                return true;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return false;
    }

    @Override
    public void addHeatMapActivity(Class<?> activity) {
        try {
            if (activity == null) {
                return;
            }

            mHeatMapActivities.add(activity.hashCode());
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void addHeatMapActivities(List<Class<?>> activitiesList) {
        try {
            if (activitiesList == null || activitiesList.size() == 0) {
                return;
            }

            for (Class<?> activity : activitiesList) {
                if (activity != null) {
                    int hashCode = activity.hashCode();
                    if (!mHeatMapActivities.contains(hashCode)) {
                        mHeatMapActivities.add(hashCode);
                    }
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public boolean isHeatMapEnabled() {
        return mXJConfigOptions.mHeatMapEnabled;
    }

    @Override
    public void enableAppHeatMapConfirmDialog(boolean enable) {
        mXJConfigOptions.enableHeatMapConfirmDialog(enable);
    }

    @Override
    public void enableHeatMap() {
        mXJConfigOptions.enableHeatMap(true);
    }

    @Override
    public String getDistinctId() {
        String loginId = getLoginId();
        if (TextUtils.isEmpty(loginId)) {// 如果从本地缓存读取失败，则尝试使用内存中的 LoginId 值
            loginId = mLoginId;
        }
        if (!TextUtils.isEmpty(loginId)) {
            return loginId;
        }
        return getAnonymousId();
    }

    @Override
    public String getAnonymousId() {
        synchronized (mDistinctId) {
            if (!mXJConfigOptions.isDataCollectEnable) {
                return "";
            }
            return mDistinctId.get();
        }
    }

    @Override
    public void resetAnonymousId() {
        synchronized (mDistinctId) {
            if (XJDataUtils.isValidAndroidId(mAndroidId)) {
                mDistinctId.commit(mAndroidId);
            } else {
                mDistinctId.commit(UUID.randomUUID().toString());
            }

            // 通知调用 resetAnonymousId 接口
            try {
                if (mEventListenerList != null) {
                    for (XJEventListener eventListener : mEventListenerList) {
                        eventListener.resetAnonymousId();
                    }
                }
            } catch (Exception e) {
                XJLog.printStackTrace(e);
            }
        }
    }

    @Override
    public String getLoginId() {
        return DbAdapter.getInstance().getLoginId();
    }

    @Override
    public void identify(final String distinctId) {
        try {
            assertValue(distinctId);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
            return;
        }

        try {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    synchronized (mDistinctId) {
                        mDistinctId.commit(distinctId);
                        // 通知调用 identify 接口
                        try {
                            if (mEventListenerList != null) {
                                for (XJEventListener eventListener : mEventListenerList) {
                                    eventListener.identify();
                                }
                            }
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                        }
                    }
                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void login(final String loginId) {
        login(loginId, null);
    }

    @Override
    public void quoteId(String quoteId) {
        mXJConfigOptions.mQuoteId=quoteId;
    }

    @Override
    public void login(final String loginId, final JSONObject properties) {
        try {
            assertValue(loginId);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
            return;
        }

        try {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    synchronized (mLoginIdLock) {
                        if (!loginId.equals(DbAdapter.getInstance().getLoginId()) && !loginId.equals(getAnonymousId())) {
                            mLoginId = loginId;
                            DbAdapter.getInstance().commitLoginId(loginId);
                            trackEvent(EventType.TRACK_SIGNUP, "$SignUp", properties, getAnonymousId());
                            // 通知调用 login 接口
                            try {
                                if (mEventListenerList != null) {
                                    for (XJEventListener eventListener : mEventListenerList) {
                                        eventListener.login();
                                    }
                                }
                            } catch (Exception e) {
                                XJLog.printStackTrace(e);
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void logout() {
        try {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    synchronized (mLoginIdLock) {
                        DbAdapter.getInstance().commitLoginId(null);
                        mLoginId = null;
                        // 进行通知调用 logout 接口
                        try {
                            if (mEventListenerList != null) {
                                for (XJEventListener eventListener : mEventListenerList) {
                                    eventListener.logout();
                                }
                            }
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                        }
                    }
                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Deprecated
    @Override
    public void trackSignUp(final String newDistinctId, final JSONObject properties) {
        try {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    String originalDistinctId = getAnonymousId();
                    synchronized (mDistinctId) {
                        mDistinctId.commit(newDistinctId);
                    }

                    trackEvent(EventType.TRACK_SIGNUP, "$SignUp", properties, originalDistinctId);

                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Deprecated
    @Override
    public void trackSignUp(final String newDistinctId) {
        try {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    String originalDistinctId = getAnonymousId();
                    synchronized (mDistinctId) {
                        mDistinctId.commit(newDistinctId);
                    }

                    trackEvent(EventType.TRACK_SIGNUP, "$SignUp", null, originalDistinctId);
                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void trackInstallation(final String eventName, final JSONObject properties, final boolean disableCallback) {
        //只在主进程触发 trackInstallation
        final JSONObject _properties = JSONUtils.makeNewObject(properties);
        addTimeProperty(_properties);
        transformInstallationTaskQueue(new Runnable() {
            @Override
            public void run() {
                if (!mIsMainProcess) {
                    return;
                }
                try {
                    boolean firstTrackInstallation;
                    if (disableCallback) {
                        firstTrackInstallation = mFirstTrackInstallationWithCallback.get();
                    } else {
                        firstTrackInstallation = mFirstTrackInstallation.get();
                    }
                    if (firstTrackInstallation) {
                        boolean isCorrectTrackInstallation = false;
                        try {
                            if (!ChannelUtils.hasUtmProperties(_properties)) {
                                ChannelUtils.mergeUtmByMetaData(mContext, _properties);
                            }

                            if (!ChannelUtils.hasUtmProperties(_properties)) {
                                String installSource;
                                String oaid;
                                if (_properties.has("$oaid")) {
                                    oaid = _properties.optString("$oaid");
                                    installSource = ChannelUtils.getDeviceInfo(mContext, mAndroidId, oaid);
                                    XJLog.i(TAG, "properties has oaid " + oaid);
                                } else {
                                    oaid = OaidHelper.getOAID(mContext);
                                    installSource = ChannelUtils.getDeviceInfo(mContext, mAndroidId, oaid);
                                }

                                if (_properties.has("$gaid")) {
                                    installSource = String.format("%s##gaid=%s", installSource, _properties.optString("$gaid"));
                                }
                                isCorrectTrackInstallation = ChannelUtils.isGetDeviceInfo(mContext, mAndroidId, oaid);
                                _properties.put("$ios_install_source", installSource);
                            }
                            if (_properties.has("$oaid")) {
                                _properties.remove("$oaid");
                            }

                            if (_properties.has("$gaid")) {
                                _properties.remove("$gaid");
                            }

                            if (disableCallback) {
                                _properties.put("$ios_install_disable_callback", disableCallback);
                            }
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                        }
                        // 先发送 track
                        trackEvent(EventType.TRACK, eventName, _properties, null);
                        // 再发送 profile_set_once 或者 profile_set
                        JSONObject profileProperties = new JSONObject();
                        // 用户属性需要去掉 $ios_install_disable_callback 字段
                        _properties.remove("$ios_install_disable_callback");
                        XJDataUtils.mergeJSONObject(_properties, profileProperties);
                        profileProperties.put("$first_visit_time", new java.util.Date());
                        if (mXJConfigOptions.mEnableMultipleChannelMatch) {
                            trackEvent(EventType.PROFILE_SET, null, profileProperties, null);
                        } else {
                            trackEvent(EventType.PROFILE_SET_ONCE, null, profileProperties, null);
                        }

                        if (disableCallback) {
                            mFirstTrackInstallationWithCallback.commit(false);
                        } else {
                            mFirstTrackInstallation.commit(false);
                        }
                        ChannelUtils.saveCorrectTrackInstallation(mContext, isCorrectTrackInstallation);
                    }
                    flushSync();
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void trackInstallation(String eventName, JSONObject properties) {
        trackInstallation(eventName, properties, false);
    }

    @Override
    public void trackInstallation(String eventName) {
        trackInstallation(eventName, null, false);
    }

    @Override
    public void trackAppInstall(JSONObject properties, final boolean disableCallback) {
        trackInstallation("$AppInstall", properties, disableCallback);
    }

    @Override
    public void trackAppInstall(JSONObject properties) {
        trackAppInstall(properties, false);
    }

    @Override
    public void trackAppInstall() {
        trackAppInstall(null, false);
    }

    public void trackChannelDebugInstallation() {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {

                try {
                    JSONObject _properties = new JSONObject();
                    _properties.put("$ios_install_source", ChannelUtils.getDeviceInfo(mContext,
                            mAndroidId, OaidHelper.getOAID(mContext)));
                    // 先发送 track
                    trackEvent(EventType.TRACK, "$ChannelDebugInstall", _properties, null);

                    // 再发送 profile_set_once 或者 profile_set
                    JSONObject profileProperties = new JSONObject();
                    XJDataUtils.mergeJSONObject(_properties, profileProperties);
                    profileProperties.put("$first_visit_time", new java.util.Date());
                    if (mXJConfigOptions.mEnableMultipleChannelMatch) {
                        trackEvent(EventType.PROFILE_SET, null, profileProperties, null);
                    } else {
                        trackEvent(EventType.PROFILE_SET_ONCE, null, profileProperties, null);
                    }
                    flushSync();
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void trackChannelEvent(String eventName) {
        trackChannelEvent(eventName, null);
    }

    @Override
    public void trackChannelEvent(final String eventName, JSONObject properties) {
        if (getConfigOptions().isAutoAddChannelCallbackEvent) {
            track(eventName, properties);
            return;
        }
        final JSONObject _properties = JSONUtils.makeNewObject(properties);
        addTimeProperty(_properties);
        transformInstallationTaskQueue(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        _properties.put("$is_channel_callback_event", ChannelUtils.isFirstChannelEvent(eventName));
                        if (!ChannelUtils.hasUtmProperties(_properties)) {
                            ChannelUtils.mergeUtmByMetaData(mContext, _properties);
                        }
                        if (!ChannelUtils.hasUtmProperties(_properties)) {
                            if (_properties.has("$oaid")) {
                                String oaid = _properties.optString("$oaid");
                                _properties.put("$channel_device_info",
                                        ChannelUtils.getDeviceInfo(mContext, mAndroidId, oaid));
                                XJLog.i(TAG, "properties has oaid " + oaid);
                            } else {
                                _properties.put("$channel_device_info",
                                        ChannelUtils.getDeviceInfo(mContext, mAndroidId, OaidHelper.getOAID(mContext)));
                            }
                        }
                        if (_properties.has("$oaid")) {
                            _properties.remove("$oaid");
                        }
                    } catch (Exception e) {
                        XJLog.printStackTrace(e);
                    }

                    // 先发送 track
                    trackEvent(EventType.TRACK, eventName, _properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void track(final String eventName, final JSONObject properties) {
        try {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    JSONObject _properties = ChannelUtils.checkOrSetChannelCallbackEvent(getConfigOptions().isAutoAddChannelCallbackEvent, eventName, properties, mContext);
                    trackEvent(EventType.TRACK, eventName, _properties, null);
                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }
     void wxeventTrack(final String eventName, final JSONObject attribute){
         try {
             mTrackTaskManager.addTrackEventTask(new Runnable() {
                 @Override
                 public void run() {
                     try {
                     JSONObject p = new JSONObject();
                     p.put("$type",API_BURY_WXEVENT_TYPE);
                     JSONObject _properties = ChannelUtils.checkOrSetChannelCallbackEvent(getConfigOptions().isAutoAddChannelCallbackEvent, eventName, p, mContext);
                         _properties.putOpt("attribute",attribute);
                         trackEvent(EventType.TRACK, eventName, _properties, null);
                         flush();
                     } catch (JSONException e) {
                         e.printStackTrace();
                     }

                 }
             });
         } catch (Exception e) {
             XJLog.printStackTrace(e);
         }
    }

    @Override
    public void track(final String eventName) {
        track(eventName, null);
    }

    @Deprecated
    @Override
    public void trackTimer(final String eventName) {
        trackTimer(eventName, TimeUnit.MILLISECONDS);
    }

    @Deprecated
    @Override
    public void trackTimer(final String eventName, final TimeUnit timeUnit) {
        final long startTime = SystemClock.elapsedRealtime();
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    assertKey(eventName);
                    synchronized (mTrackTimer) {
                        mTrackTimer.put(eventName, new EventTimer(timeUnit, startTime));
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void removeTimer(final String eventName) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    assertKey(eventName);
                    synchronized (mTrackTimer) {
                        mTrackTimer.remove(eventName);
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public String trackTimerStart(String eventName) {
        try {
            final String eventNameRegex = String.format("%s_%s_%s", eventName, UUID.randomUUID().toString().replace("-", "_"), "SATimer");
            trackTimerBegin(eventNameRegex, TimeUnit.SECONDS);
            trackTimerBegin(eventName, TimeUnit.SECONDS);
            return eventNameRegex;
        } catch (Exception ex) {
            XJLog.printStackTrace(ex);
        }
        return "";
    }

    @Override
    public void trackTimerPause(String eventName) {
        trackTimerState(eventName, true);
    }

    @Override
    public void trackTimerResume(String eventName) {
        trackTimerState(eventName, false);
    }

    @Override
    public void apiBuryWxshare(String shareUUid, String fromSharePageUrl, String fromSharePageName, String from, String url) {
        JSONObject jsonObject=new JSONObject();
        try {
            jsonObject.put("$shareUUid",shareUUid);
            jsonObject.put("$fromSharePageUrl",fromSharePageUrl);
            jsonObject.put("$fromSharePageName",fromSharePageName);
            jsonObject.put("$from",from);
            jsonObject.put("$url",url);
        }catch (Exception e){}
        track(API_BURY_WXSHARE,jsonObject);
            flush();
    }

    @Override
    public void apiBuryWxuser(String shareUUid, String fromSharePageUrl, String fromSharePageName, String url, String systemid,
                              String unionid, String openid, String nickName, String headImgUrl, String mobile, String email, String sex) {
        JSONObject jsonObject=new JSONObject();
        try {
            jsonObject.put("$shareUUid",shareUUid);
            jsonObject.put("$fromSharePageUrl",fromSharePageUrl);
            jsonObject.put("$fromSharePageName",fromSharePageName);
            jsonObject.put("$systemid",systemid);
            jsonObject.put("$url",url);
            jsonObject.put("$unionid",unionid);
            jsonObject.put("$openid",openid);
            jsonObject.put("$nickName",nickName);
            jsonObject.put("$headImgUrl",headImgUrl);
            jsonObject.put("$mobile",mobile);
            jsonObject.put("$email",email);
            jsonObject.put("$sex",sex);
            mXJConfigOptions.email=email;
            mXJConfigOptions.mobile=mobile;
            mXJConfigOptions.openid=openid;
            mXJConfigOptions.unionid=unionid;
        }catch (Exception e){}
        track(API_BURY_WXUSER,jsonObject);
            flush();
    }

    /**
     * 初始化事件的计时器，默认计时单位为毫秒。
     * 详细用法请参考 trackTimerBegin(String, TimeUnit)
     *
     * @param eventName 事件的名称
     */
    @Override
    @Deprecated
    public void trackTimerBegin(final String eventName) {
        trackTimer(eventName);
    }

    @Override
    @Deprecated
    public void trackTimerBegin(final String eventName, final TimeUnit timeUnit) {
        trackTimer(eventName, timeUnit);
    }

    @Override
    public void trackTimerEnd(final String eventName, final JSONObject properties) {
        final long endTime = SystemClock.elapsedRealtime();
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                if (eventName != null) {
                    synchronized (mTrackTimer) {
                        EventTimer eventTimer = mTrackTimer.get(eventName);
                        if (eventTimer != null) {
                            eventTimer.setEndTime(endTime);
                        }
                    }
                }
                try {
                    JSONObject _properties = ChannelUtils.checkOrSetChannelCallbackEvent(getConfigOptions().isAutoAddChannelCallbackEvent, eventName, properties, mContext);
                    trackEvent(EventType.TRACK, eventName, _properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void trackTimerEnd(final String eventName) {
        trackTimerEnd(eventName, null);
    }

    @Override
    public void clearTrackTimer() {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mTrackTimer) {
                        mTrackTimer.clear();
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public String getLastScreenUrl() {
        return mLastScreenUrl;
    }

    @Override
    public void clearReferrerWhenAppEnd() {
        mClearReferrerWhenAppEnd = true;
    }

    @Override
    public void clearLastScreenUrl() {
        if (mClearReferrerWhenAppEnd) {
            mLastScreenUrl = null;
        }
    }

    @Override
    @Deprecated
    public String getMainProcessName() {
        return mMainProcessName;
    }

    @Override
    public JSONObject getLastScreenTrackProperties() {
        return mLastScreenTrackProperties;
    }

    @Override
    @Deprecated
    public void trackViewScreen(final String url, final JSONObject properties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!TextUtils.isEmpty(url) || properties != null) {
                        String currentUrl = url;
                        JSONObject trackProperties = new JSONObject();
                        mLastScreenTrackProperties = properties;

                        if (mLastScreenUrl != null) {
                            trackProperties.put("$referrer", mLastScreenUrl);
                        }

                        mReferrerScreenTitle = mCurrentScreenTitle;
                        if (properties != null) {
                            if (properties.has("$title")) {
                                mCurrentScreenTitle = properties.getString("$title");
                            } else {
                                mCurrentScreenTitle = null;
                            }
                            if (properties.has("$url")) {
                                currentUrl = properties.optString("$url");
                            }
                        }
                        trackProperties.put("$url", currentUrl);
                        mLastScreenUrl = currentUrl;
                        if (properties != null) {
                            XJDataUtils.mergeJSONObject(properties, trackProperties);
                        }
                        trackEvent(EventType.TRACK, "$AppViewScreen", trackProperties, null);//页面切换自动上报
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void trackViewScreen(final Activity activity) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    if (activity == null) {
                        return;
                    }
                    JSONObject properties = AopUtil.buildTitleAndScreenName(activity);
                    trackViewScreen(XJDataUtils.getScreenUrl(activity), properties);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void trackViewScreen(final Object fragment) {
        if (fragment == null) {
            return;
        }

        Class<?> supportFragmentClass = null;
        Class<?> appFragmentClass = null;
        Class<?> androidXFragmentClass = null;

        try {
            try {
                supportFragmentClass = Class.forName("android.support.v4.app.Fragment");
            } catch (Exception e) {
                //ignored
            }

            try {
                appFragmentClass = Class.forName("android.app.Fragment");
            } catch (Exception e) {
                //ignored
            }

            try {
                androidXFragmentClass = Class.forName("androidx.fragment.app.Fragment");
            } catch (Exception e) {
                //ignored
            }
        } catch (Exception e) {
            //ignored
        }

        if (!(supportFragmentClass != null && supportFragmentClass.isInstance(fragment)) &&
                !(appFragmentClass != null && appFragmentClass.isInstance(fragment)) &&
                !(androidXFragmentClass != null && androidXFragmentClass.isInstance(fragment))) {
            return;
        }

        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject properties = new JSONObject();
                    String screenName = fragment.getClass().getCanonicalName();

                    String title = null;

                    if (fragment.getClass().isAnnotationPresent(XJDataFragmentTitle.class)) {
                        XJDataFragmentTitle XJDataFragmentTitle = fragment.getClass().getAnnotation(XJDataFragmentTitle.class);
                        if (XJDataFragmentTitle != null) {
                            title = XJDataFragmentTitle.title();
                        }
                    }

                    if (Build.VERSION.SDK_INT >= 11) {
                        Activity activity = null;
                        try {
                            Method getActivityMethod = fragment.getClass().getMethod("getActivity");
                            if (getActivityMethod != null) {
                                activity = (Activity) getActivityMethod.invoke(fragment);
                            }
                        } catch (Exception e) {
                            //ignored
                        }
                        if (activity != null) {
                            if (TextUtils.isEmpty(title)) {
                                title = XJDataUtils.getActivityTitle(activity);
                            }
                            screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), screenName);
                        }
                    }

                    if (!TextUtils.isEmpty(title)) {
                        properties.put(AopConstants.TITLE, title);
                    }
                    properties.put("$screen_name", screenName);
                    if (fragment instanceof XJAutoTracker) {
                        XJAutoTracker XJAutoTracker = (XJAutoTracker) fragment;
                        JSONObject otherProperties = XJAutoTracker.getTrackProperties();
                        if (otherProperties != null) {
                            XJDataUtils.mergeJSONObject(otherProperties, properties);
                        }
                    }
                    trackViewScreen(XJDataUtils.getScreenUrl(fragment), properties);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void trackViewAppClick(View view) {
        trackViewAppClick(view, null);
    }

    @Override
    public void trackViewAppClick(final View view, JSONObject properties) {
        if (view == null) {
            return;
        }
        if (properties == null) {
            properties = new JSONObject();
        }
        if (AopUtil.injectClickInfo(view, properties, true)) {
            trackInternal(AopConstants.APP_CLICK_EVENT_NAME, properties);
        }
    }

    @Override
    public void flush() {
        mMessages.flush();
    }

    @Override
    public void flushSync() {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                mMessages.flush();
            }
        });
    }

    @Override
    public void registerDynamicSuperProperties(XJDataDynamicSuperProperties dynamicSuperProperties) {
        mDynamicSuperPropertiesCallBack = dynamicSuperProperties;
    }

    @Override
    public void setTrackEventCallBack(XJDataTrackEventCallBack trackEventCallBack) {
        mTrackEventCallBack = trackEventCallBack;
    }

    @Override
    public void setDeepLinkCallback(XJDataDeepLinkCallback deepLinkCallback) {
        mDeepLinkCallback = deepLinkCallback;
    }

    @Override
    public void stopTrackThread() {
        if (mTrackTaskManagerThread != null && !mTrackTaskManagerThread.isStopped()) {
            mTrackTaskManagerThread.stop();
            XJLog.i(TAG, "Data collection thread has been stopped");
        }
    }

    @Override
    public void startTrackThread() {
        if (mTrackTaskManagerThread == null || mTrackTaskManagerThread.isStopped()) {
            mTrackTaskManagerThread = new TrackTaskManagerThread();
            new Thread(mTrackTaskManagerThread).start();
            XJLog.i(TAG, "Data collection thread has been started");
        }
    }

    @Override
    public void enableDataCollect() {
        try {
            if (!mXJConfigOptions.isDataCollectEnable) {
                mContext.getContentResolver().notifyChange(DbParams.getInstance().getDataCollectUri(), null);
            }
            mXJConfigOptions.isDataCollectEnable = true;
            mAndroidId = XJDataUtils.getAndroidID(mContext);
            mDeviceInfo = setupDeviceInfo();
            mTrackTaskManager.setDataCollectEnable(true);
            // 同意合规时更新首日首次
            if (mFirstDay.get() == null) {
                mFirstDay.commit(TimeUtils.formatTime(System.currentTimeMillis(), TimeUtils.YYYY_MM_DD));
            }
        } catch (Exception ex) {
            XJLog.printStackTrace(ex);
        }
    }

    @Override
    public void deleteAll() {
        mMessages.deleteAll();
    }

    @Override
    public JSONObject getSuperProperties() {
        synchronized (mSuperProperties) {
            try {
                return new JSONObject(mSuperProperties.get().toString());
            } catch (JSONException e) {
                XJLog.printStackTrace(e);
                return new JSONObject();
            }
        }
    }

    @Override
    public void registerSuperProperties(final JSONObject superProperties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    if (superProperties == null) {
                        return;
                    }
                    assertPropertyTypes(superProperties);
                    synchronized (mSuperProperties) {
                        JSONObject properties = mSuperProperties.get();
                        mSuperProperties.commit(XJDataUtils.mergeSuperJSONObject(superProperties, properties));
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void unregisterSuperProperty(final String superPropertyName) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mSuperProperties) {
                        JSONObject superProperties = mSuperProperties.get();
                        superProperties.remove(superPropertyName);
                        mSuperProperties.commit(superProperties);
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void clearSuperProperties() {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                synchronized (mSuperProperties) {
                    mSuperProperties.commit(new JSONObject());
                }
            }
        });
    }

    @Override
    public void profileSet(final JSONObject properties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_SET, null, properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileSet(final String property, final Object value) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_SET, null, new JSONObject().put(property, value), null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileSetOnce(final JSONObject properties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_SET_ONCE, null, properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileSetOnce(final String property, final Object value) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_SET_ONCE, null, new JSONObject().put(property, value), null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileIncrement(final Map<String, ? extends Number> properties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_INCREMENT, null, new JSONObject(properties), null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileIncrement(final String property, final Number value) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_INCREMENT, null, new JSONObject().put(property, value), null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileAppend(final String property, final String value) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray append_values = new JSONArray();
                    append_values.put(value);
                    final JSONObject properties = new JSONObject();
                    properties.put(property, append_values);
                    trackEvent(EventType.PROFILE_APPEND, null, properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileAppend(final String property, final Set<String> values) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray append_values = new JSONArray();
                    for (String value : values) {
                        append_values.put(value);
                    }
                    final JSONObject properties = new JSONObject();
                    properties.put(property, append_values);
                    trackEvent(EventType.PROFILE_APPEND, null, properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileUnset(final String property) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_UNSET, null, new JSONObject().put(property, true), null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileDelete() {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.PROFILE_DELETE, null, null, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public boolean isDebugMode() {
        return mDebugMode.isDebugMode();
    }

    @Override
    public boolean isNetworkRequestEnable() {
        return mEnableNetworkRequest;
    }

    @Override
    public void enableNetworkRequest(boolean isRequest) {
        this.mEnableNetworkRequest = isRequest;
    }

    @Override
    public void setServerUrl(String serverUrl) {
        setServerUrl(serverUrl, false);
    }

    @Override
    public void setServerUrl(String serverUrl, boolean isRequestRemoteConfig) {
        try {
            //请求远程配置
            if (isRequestRemoteConfig && mRemoteManager != null) {
                try {
                    mRemoteManager.requestRemoteConfig(BaseXJDataSDKRemoteManager.RandomTimeType.RandomTimeTypeWrite, false);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
            mOriginServerUrl = serverUrl;
            if (TextUtils.isEmpty(serverUrl)) {
                mServerUrl = serverUrl;
                XJLog.i(TAG, "Server url is null or empty.");
                return;
            }

            Uri serverURI = Uri.parse(serverUrl);
            String hostServer = serverURI.getHost();
            if (!TextUtils.isEmpty(hostServer) && hostServer.contains("_")) {
                XJLog.i(TAG, "Server url " + serverUrl + " contains '_' is not recommend，" +
                        "see details: https://en.wikipedia.org/wiki/Hostname");
            }

            if (mDebugMode != DebugMode.DEBUG_OFF) {
                String uriPath = serverURI.getPath();
                if (TextUtils.isEmpty(uriPath)) {
                    return;
                }

                int pathPrefix = uriPath.lastIndexOf('/');
                if (pathPrefix != -1) {
                    String newPath = uriPath.substring(0, pathPrefix) + "/debug";
                    // 将 URI Path 中末尾的部分替换成 '/debug'
                    mServerUrl = serverURI.buildUpon().path(newPath).build().toString();
                }
            } else {
                mServerUrl = serverUrl;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void trackEventFromH5(String eventInfo, boolean enableVerify) {
        try {
            if (TextUtils.isEmpty(eventInfo)) {
                return;
            }

            JSONObject eventObject = new JSONObject(eventInfo);
            if (enableVerify) {
                String serverUrl = eventObject.optString("server_url");
                if (!TextUtils.isEmpty(serverUrl)) {
                    if (!(new ServerUrl(serverUrl).check(new ServerUrl(mServerUrl)))) {
                        return;
                    }
                } else {
                    //防止 H5 集成的 JS SDK 版本太老，没有发 server_url
                    return;
                }
            }
            trackEventFromH5(eventInfo);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    @Override
    public void trackEventFromH5(String eventInfo) {
        trackEventH5(eventInfo);
    }

    @Override
    public void profilePushId(final String pushTypeKey, final String pushId) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    assertKey(pushTypeKey);
                    if (TextUtils.isEmpty(pushId)) {
                        XJLog.d(TAG, "pushId is empty");
                        return;
                    }
                    String distinctId = getDistinctId();
                    String distinctPushId = distinctId + pushId;
                    SharedPreferences sp = XJDataUtils.getSharedPreferences(mContext);
                    String spDistinctPushId = sp.getString("distinctId_" + pushTypeKey, "");
                    if (!spDistinctPushId.equals(distinctPushId)) {
                        profileSet(pushTypeKey, pushId);
                        sp.edit().putString("distinctId_" + pushTypeKey, distinctPushId).apply();
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void profileUnsetPushId(final String pushTypeKey) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    assertKey(pushTypeKey);
                    String distinctId = getDistinctId();
                    SharedPreferences sp = XJDataUtils.getSharedPreferences(mContext);
                    String key = "distinctId_" + pushTypeKey;
                    String spDistinctPushId = sp.getString(key, "");

                    if (spDistinctPushId.startsWith(distinctId)) {
                        profileUnset(pushTypeKey);
                        sp.edit().remove(key).apply();
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    @Override
    public void itemSet(final String itemType, final String itemId, final JSONObject properties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                trackItemEvent(itemType, itemId, EventType.ITEM_SET.getEventType(), System.currentTimeMillis(), properties);
            }
        });
    }

    @Override
    public void itemDelete(final String itemType, final String itemId) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                trackItemEvent(itemType, itemId, EventType.ITEM_DELETE.getEventType(), System.currentTimeMillis(), null);
            }
        });
    }

    /**
     * 不能动位置，因为 SF 反射获取使用
     *
     * @return ServerUrl
     */
    public String getServerUrl() {
        return mServerUrl;
    }

    /**
     * 获取 SDK 的版本号
     *
     * @return SDK 的版本号
     */
    public String getSDKVersion() {
        return VERSION;
    }

    /**
     * Debug 模式，用于检验数据导入是否正确。该模式下，事件会逐条实时发送到 Sensors Analytics，并根据返回值检查
     * 数据导入是否正确。
     * Debug 模式的具体使用方式，请参考:
     * http://www.sensorsdata.cn/manual/debug_mode.html
     * Debug 模式有三种：
     * DEBUG_OFF - 关闭DEBUG模式
     * DEBUG_ONLY - 打开DEBUG模式，但该模式下发送的数据仅用于调试，不进行数据导入
     * DEBUG_AND_TRACK - 打开DEBUG模式，并将数据导入到SensorsAnalytics中
     */
    public enum DebugMode {
        DEBUG_OFF(false, false),
        DEBUG_ONLY(true, false),
        DEBUG_AND_TRACK(true, true);

        private final boolean debugMode;
        private final boolean debugWriteData;

        DebugMode(boolean debugMode, boolean debugWriteData) {
            this.debugMode = debugMode;
            this.debugWriteData = debugWriteData;
        }

        boolean isDebugMode() {
            return debugMode;
        }

        boolean isDebugWriteData() {
            return debugWriteData;
        }
    }

    /**
     * AutoTrack 默认采集的事件类型
     */
    public enum AutoTrackEventType {
        APP_START(1),
        APP_END(1 << 1),
        APP_CLICK(1 << 2),
        APP_VIEW_SCREEN(1 << 3);
        private final int eventValue;

        AutoTrackEventType(int eventValue) {
            this.eventValue = eventValue;
        }

        static AutoTrackEventType autoTrackEventTypeFromEventName(String eventName) {
            if (TextUtils.isEmpty(eventName)) {
                return null;
            }

            switch (eventName) {
                case "$AppStart":
                    return APP_START;
                case "$AppEnd":
                    return APP_END;
                case "$AppClick":
                    return APP_CLICK;
                case "$AppViewScreen":
                    return APP_VIEW_SCREEN;
                default:
                    break;
            }

            return null;
        }

        static String autoTrackEventName(int eventType) {
            switch (eventType) {
                case 1:
                    return "$AppStart";
                case 2:
                    return "$AppEnd";
                case 4:
                    return "$AppClick";
                case 8:
                    return "$AppViewScreen";
                default:
                    return "";
            }
        }

        static boolean isAutoTrackType(String eventName) {
            if (!TextUtils.isEmpty(eventName)) {
                switch (eventName) {
                    case "$AppStart":
                    case "$AppEnd":
                    case "$AppClick":
                    case "$AppViewScreen":
                        return true;
                    default:
                        break;
                }
            }
            return false;
        }

        int getEventValue() {
            return eventValue;
        }
    }

    @Override
    public void apiBuryWxevent(String eventId) {
        JSONObject jsonObject=new JSONObject();
        try {
            jsonObject.put("$eventId",eventId);
        }catch (Exception e){}
        track(API_BURY_WXEVENT,jsonObject);
        flush();
        if (TIME_LONG_EVENT.equals(eventId)){//app时长
            timer.start();
        }else if (PAGE_CHANGE_EVENT.equals(eventId)){//切换页面
        }
    }


    @Override
    public void apiBuryWxevent(String eventId,JSONObject attribute) {
        wxeventTrack(eventId,attribute);
    }
    CountDownTimer timer=new CountDownTimer(8*1000,1000) {
        @Override
        public void onTick(long millisUntilFinished) {
        }
        @Override
        public void onFinish() {
            XJDataAPI.sharedInstance().apiBuryWxevent(TIME_LONG_EVENT);//时长(初始化后每五秒上报一次)
        }
    };
    /**
     * 网络类型
     */
    public final class NetworkType {
        public static final int TYPE_NONE = 0;//NULL
        public static final int TYPE_2G = 1;//2G
        public static final int TYPE_3G = 1 << 1;//3G
        public static final int TYPE_4G = 1 << 2;//4G
        public static final int TYPE_WIFI = 1 << 3;//WIFI
        public static final int TYPE_5G = 1 << 4;//5G
        public static final int TYPE_ALL = 0xFF;//ALL
    }
}