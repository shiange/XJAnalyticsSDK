/*
 * Created by dengshiwei on 2020/10/20.
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

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.xjdata.analytics.android.sdk.data.DbAdapter;
import com.xjdata.analytics.android.sdk.data.DbParams;
import com.xjdata.analytics.android.sdk.data.PersistentLoader;
import com.xjdata.analytics.android.sdk.data.persistent.PersistentDistinctId;
import com.xjdata.analytics.android.sdk.data.persistent.PersistentFirstDay;
import com.xjdata.analytics.android.sdk.data.persistent.PersistentFirstStart;
import com.xjdata.analytics.android.sdk.data.persistent.PersistentFirstTrackInstallation;
import com.xjdata.analytics.android.sdk.data.persistent.PersistentFirstTrackInstallationWithCallback;
import com.xjdata.analytics.android.sdk.data.persistent.PersistentSuperProperties;
import com.xjdata.analytics.android.sdk.deeplink.XJDataDeepLinkCallback;
import com.xjdata.analytics.android.sdk.encrypt.XJDataEncrypt;
import com.xjdata.analytics.android.sdk.exceptions.InvalidDataException;
import com.xjdata.analytics.android.sdk.internal.api.FragmentAPI;
import com.xjdata.analytics.android.sdk.internal.api.IFragmentAPI;
import com.xjdata.analytics.android.sdk.internal.rpc.XJDataContentObserver;
import com.xjdata.analytics.android.sdk.listener.XJEventListener;
import com.xjdata.analytics.android.sdk.listener.XJJSListener;
import com.xjdata.analytics.android.sdk.remote.BaseXJDataSDKRemoteManager;
import com.xjdata.analytics.android.sdk.remote.XJDataRemoteManager;
import com.xjdata.analytics.android.sdk.util.AppInfoUtils;
import com.xjdata.analytics.android.sdk.util.ChannelUtils;
import com.xjdata.analytics.android.sdk.util.DeviceUtils;
import com.xjdata.analytics.android.sdk.util.JSONUtils;
import com.xjdata.analytics.android.sdk.util.NetworkUtils;
import com.xjdata.analytics.android.sdk.util.OaidHelper;
import com.xjdata.analytics.android.sdk.util.XJDataHelper;
import com.xjdata.analytics.android.sdk.util.XJDataUtils;
import com.xjdata.analytics.android.sdk.util.TimeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.xjdata.analytics.android.sdk.util.XJDataHelper.assertKey;
import static com.xjdata.analytics.android.sdk.util.XJDataHelper.assertPropertyTypes;
import static com.xjdata.analytics.android.sdk.util.XJDataHelper.assertValue;

public abstract class AbstractXJDataAPI implements IXJDataAPI {
    protected static final String TAG = "XJDataAPI";
    public static final String API_BURY_WXATTRIBUTE="api_bury_wxattribute";//初始化sdk接口
    public static final String API_BURY_WXSHARE="api_bury_wxshare";//分享者信息接口
    public static final String API_BURY_WXUSER="api_bury_wxuser";//上报用户信息接口
    public static final String API_BURY_WXEVENT="api_bury_wxevent";//上报事件接口

    public static final String PAGE_CHANGE_EVENT="pageChange";//切换页面事件
    public static final String TIME_LONG_EVENT="timeLong";//app时长

    public static  boolean INIT_SUCCESS=false;//是否初始化成功

    // SDK版本
    static final String VERSION = BuildConfig.SDK_VERSION;
    // Maps each token to a singleton SensorsDataAPI instance
    protected static final Map<Context, XJDataAPI> sInstanceMap = new HashMap<>();
    static boolean mIsMainProcess = false;
    static boolean SHOW_DEBUG_INFO_VIEW = true;
    protected static XJDataGPSLocation mGPSLocation;
    /* 远程配置 */
    protected static XJConfigOptions mXJConfigOptions;
    protected final Context mContext;
    protected AnalyticsMessages mMessages;
    protected final PersistentDistinctId mDistinctId;
    protected final PersistentSuperProperties mSuperProperties;
    protected final PersistentFirstStart mFirstStart;
    protected final PersistentFirstDay mFirstDay;
    protected final PersistentFirstTrackInstallation mFirstTrackInstallation;
    protected final PersistentFirstTrackInstallationWithCallback mFirstTrackInstallationWithCallback;
    protected Map<String, Object> mDeviceInfo;
    protected final Map<String, EventTimer> mTrackTimer;
    protected final Object mLoginIdLock = new Object();
    protected List<Class> mIgnoredViewTypeList = new ArrayList<>();
    /* AndroidID */
    protected String mAndroidId = null;
    /* LoginId */
    protected String mLoginId = null;
    /* SensorsAnalytics 地址 */
    protected String mServerUrl;
    protected String mOriginServerUrl;
    /* SDK 配置是否初始化 */
    protected boolean mSDKConfigInit;
    /* Debug 模式选项 */
    protected XJDataAPI.DebugMode mDebugMode = XJDataAPI.DebugMode.DEBUG_OFF;
    /* SDK 自动采集事件 */
    protected boolean mAutoTrack;
    /* 上个页面的 Url */
    protected String mLastScreenUrl;
    /* 上个页面的 Title */
    protected String mReferrerScreenTitle;
    /* 当前页面的 Title */
    protected String mCurrentScreenTitle;
    protected JSONObject mLastScreenTrackProperties;
    /* 是否请求网络 */
    protected boolean mEnableNetworkRequest = true;
    protected boolean mClearReferrerWhenAppEnd = false;
    protected boolean mDisableDefaultRemoteConfig = false;
    protected boolean mDisableTrackDeviceId = false;
    // Session 时长
    protected int mSessionTime = 30 * 1000;
    protected List<Integer> mAutoTrackIgnoredActivities;
    protected List<Integer> mHeatMapActivities;
    protected List<Integer> mVisualizedAutoTrackActivities;
    /* 主进程名称 */
    protected String mMainProcessName;
    protected String mCookie;
    protected TrackTaskManager mTrackTaskManager;
    protected TrackTaskManagerThread mTrackTaskManagerThread;
    protected XJDataScreenOrientationDetector mOrientationDetector;
    protected XJDataDynamicSuperProperties mDynamicSuperPropertiesCallBack;
    protected SimpleDateFormat mIsFirstDayDateFormat;
    protected XJDataTrackEventCallBack mTrackEventCallBack;
    protected List<XJEventListener> mEventListenerList;
    private CopyOnWriteArrayList<XJJSListener> mXJJSListeners;
    protected IFragmentAPI mFragmentAPI;
    XJDataEncrypt mXJDataEncrypt;
    protected XJDataDeepLinkCallback mDeepLinkCallback;
    BaseXJDataSDKRemoteManager mRemoteManager;
    /**
     * 标记是否已经采集了带有插件版本号的事件
     */
    private boolean isTrackEventWithPluginVersion = false;

    public AbstractXJDataAPI(Context context, String serverURL, XJDataAPI.DebugMode debugMode) {
        mContext = context;
        setDebugMode(debugMode);
        final String packageName = context.getApplicationContext().getPackageName();
        mAutoTrackIgnoredActivities = new ArrayList<>();
        mHeatMapActivities = new ArrayList<>();
        mVisualizedAutoTrackActivities = new ArrayList<>();
        PersistentLoader.initLoader(context);
        mDistinctId = (PersistentDistinctId) PersistentLoader.loadPersistent(PersistentLoader.PersistentName.DISTINCT_ID);
        mSuperProperties = (PersistentSuperProperties) PersistentLoader.loadPersistent(PersistentLoader.PersistentName.SUPER_PROPERTIES);
        mFirstStart = (PersistentFirstStart) PersistentLoader.loadPersistent(PersistentLoader.PersistentName.FIRST_START);
        mFirstTrackInstallation = (PersistentFirstTrackInstallation) PersistentLoader.loadPersistent(PersistentLoader.PersistentName.FIRST_INSTALL);
        mFirstTrackInstallationWithCallback = (PersistentFirstTrackInstallationWithCallback) PersistentLoader.loadPersistent(PersistentLoader.PersistentName.FIRST_INSTALL_CALLBACK);
        mFirstDay = (PersistentFirstDay) PersistentLoader.loadPersistent(PersistentLoader.PersistentName.FIRST_DAY);
        mTrackTimer = new HashMap<>();
        mFragmentAPI = new FragmentAPI();
        try {
            mTrackTaskManager = TrackTaskManager.getInstance();
            mTrackTaskManagerThread = new TrackTaskManagerThread();
            new Thread(mTrackTaskManagerThread, ThreadNameConstants.THREAD_TASK_QUEUE).start();
            XJDataExceptionHandler.init();
            initSAConfig(serverURL, packageName);
            mMessages = AnalyticsMessages.getInstance(mContext, (XJDataAPI) this);
            mRemoteManager = new XJDataRemoteManager((XJDataAPI) this);
            //先从缓存中读取 SDKConfig
            mRemoteManager.applySDKConfigFromCache();

            //打开 debug 模式，弹出提示
            if (mDebugMode != XJDataAPI.DebugMode.DEBUG_OFF && mIsMainProcess) {
                if (SHOW_DEBUG_INFO_VIEW) {
                    if (!isSDKDisabled()) {
                        showDebugModeWarning();
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                final Application app = (Application) context.getApplicationContext();
                final XJDataActivityLifecycleCallbacks lifecycleCallbacks =
                        new XJDataActivityLifecycleCallbacks((XJDataAPI) this, mFirstStart, mFirstDay, context);
                app.registerActivityLifecycleCallbacks(lifecycleCallbacks);
                app.registerActivityLifecycleCallbacks(AppStateManager.getInstance());
            }

            registerObserver();
            XJLog.i(TAG, String.format(Locale.CHINA, "Initialized the instance of Sensors Analytics SDK with server"
                    + " url '%s', flush interval %d ms, debugMode: %s", mServerUrl, mXJConfigOptions.mFlushInterval, debugMode));
            if (mXJConfigOptions.isDataCollectEnable) {
                mAndroidId = XJDataUtils.getAndroidID(mContext);
                mDeviceInfo = setupDeviceInfo();
            }
        } catch (Exception ex) {
            XJLog.printStackTrace(ex);
        }
        initSdkWxattribute();
    }

    protected AbstractXJDataAPI() {
        mContext = null;
        mMessages = null;
        mDistinctId = null;
        mSuperProperties = null;
        mFirstStart = null;
        mFirstDay = null;
        mFirstTrackInstallation = null;
        mFirstTrackInstallationWithCallback = null;
        mDeviceInfo = null;
        mTrackTimer = null;
        mMainProcessName = null;
        mXJDataEncrypt = null;
    }

    /**
     * 返回是否关闭了 SDK
     *
     * @return true：关闭；false：没有关闭
     */
    public static boolean isSDKDisabled() {
        boolean isSDKDisabled = XJDataRemoteManager.isSDKDisabledByRemote();
        if (isSDKDisabled) {
            XJLog.i(TAG, "remote config: SDK is disabled");
        }
        return isSDKDisabled;
    }

    /**
     * SDK 事件回调监听，目前用于弹窗业务
     *
     * @param eventListener 事件监听
     */
    public void addEventListener(XJEventListener eventListener) {
        try {
            if (this.mEventListenerList == null) {
                this.mEventListenerList = new ArrayList<>();
            }
            this.mEventListenerList.add(eventListener);
        } catch (Exception ex) {
            XJLog.printStackTrace(ex);
        }
    }

    /**
     * 监听 JS 消息
     *
     * @param listener JS 监听
     */
    public void addSAJSListener(final XJJSListener listener) {
        try {
            if (mXJJSListeners == null) {
                mXJJSListeners = new CopyOnWriteArrayList<>();
            }
            if (!mXJJSListeners.contains(listener)) {
                mXJJSListeners.add(listener);
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    void handleJsMessage(WeakReference<View> view, final String message) {
        if (mXJJSListeners != null && mXJJSListeners.size() > 0) {
            for (final XJJSListener listener : mXJJSListeners) {
                try {
                    if (listener != null) {
                        listener.onReceiveJSMessage(view, message);
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        }
    }

    public static XJConfigOptions getConfigOptions() {
        return mXJConfigOptions;
    }

    public Context getContext() {
        return mContext;
    }

    boolean isSaveDeepLinkInfo() {
        return mXJConfigOptions.mEnableSaveDeepLinkInfo;
    }

    XJDataDeepLinkCallback getDeepLinkCallback() {
        return mDeepLinkCallback;
    }

    boolean isMultiProcessFlushData() {
        return mXJConfigOptions.isSubProcessFlushData;
    }

    boolean _trackEventFromH5(String eventInfo) {
        try {
            if (TextUtils.isEmpty(eventInfo)) {
                return false;
            }
            JSONObject eventObject = new JSONObject(eventInfo);

            String serverUrl = eventObject.optString("server_url");
            if (!TextUtils.isEmpty(serverUrl)) {
                if (!(new ServerUrl(serverUrl).check(new ServerUrl(mServerUrl)))) {
                    return false;
                }
                trackEventFromH5(eventInfo);
                return true;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return false;
    }

    /**
     * SDK 内部用来调用触发事件
     *
     * @param eventName 事件名称
     * @param properties 事件属性
     */
    public void trackInternal(final String eventName, final JSONObject properties) {
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    trackEvent(EventType.TRACK, eventName, properties, null);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    public XJDataAPI.DebugMode getDebugMode() {
        return mDebugMode;
    }

    public void setDebugMode(XJDataAPI.DebugMode debugMode) {
        mDebugMode = debugMode;
        if (debugMode == XJDataAPI.DebugMode.DEBUG_OFF) {
            enableLog(false);
            XJLog.setDebug(false);
            mServerUrl = mOriginServerUrl;
        } else {
            enableLog(true);
            XJLog.setDebug(true);
            setServerUrl(mOriginServerUrl);
        }
    }

    void enableAutoTrack(int autoTrackEventType) {
        try {
            if (autoTrackEventType <= 0 || autoTrackEventType > 15) {
                return;
            }
            this.mAutoTrack = true;
            mXJConfigOptions.setAutoTrackEventType(mXJConfigOptions.mAutoTrackEventType | autoTrackEventType);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    /**
     * 返回是否开启点击图的提示框
     *
     * @return true 代表开启了点击图的提示框， false 代表关闭了点击图的提示框
     */
    public boolean isAppHeatMapConfirmDialogEnabled() {
        return mXJConfigOptions.mHeatMapConfirmDialogEnabled;
    }

    public boolean isVisualizedAutoTrackConfirmDialogEnabled() {
        return mXJConfigOptions.mVisualizedConfirmDialogEnabled;
    }

    public BaseXJDataSDKRemoteManager getRemoteManager() {
        return mRemoteManager;
    }

    public void setRemoteManager(BaseXJDataSDKRemoteManager remoteManager) {
        this.mRemoteManager = remoteManager;
    }

    public XJDataEncrypt getSensorsDataEncrypt() {
        return mXJDataEncrypt;
    }

    public boolean isDisableDefaultRemoteConfig() {
        return mDisableDefaultRemoteConfig;
    }

    /**
     * App 从后台恢复，遍历 mTrackTimer
     * startTime = System.currentTimeMillis()
     */
    void appBecomeActive() {
        synchronized (mTrackTimer) {
            try {
                for (Map.Entry<String, EventTimer> entry : mTrackTimer.entrySet()) {
                    if (entry != null) {
                        EventTimer eventTimer = (EventTimer) entry.getValue();
                        if (eventTimer != null) {
                            eventTimer.setStartTime(SystemClock.elapsedRealtime());
                        }
                    }
                }
            } catch (Exception e) {
                XJLog.i(TAG, "appBecomeActive error:" + e.getMessage());
            }
        }
    }

    /**
     * App 进入后台，遍历 mTrackTimer
     * eventAccumulatedDuration =
     * eventAccumulatedDuration + System.currentTimeMillis() - startTime - SessionIntervalTime
     */
    void appEnterBackground() {
        synchronized (mTrackTimer) {
            try {
                for (Map.Entry<String, EventTimer> entry : mTrackTimer.entrySet()) {
                    if (entry != null) {
                        if ("$AppEnd".equals(entry.getKey())) {
                            continue;
                        }
                        EventTimer eventTimer = (EventTimer) entry.getValue();
                        if (eventTimer != null && !eventTimer.isPaused()) {
                            long eventAccumulatedDuration =
                                    eventTimer.getEventAccumulatedDuration() + SystemClock.elapsedRealtime() - eventTimer.getStartTime() - getSessionIntervalTime();
                            eventTimer.setEventAccumulatedDuration(eventAccumulatedDuration);
                            eventTimer.setStartTime(SystemClock.elapsedRealtime());
                        }
                    }
                }
            } catch (Exception e) {
                XJLog.i(TAG, "appEnterBackground error:" + e.getMessage());
            }
        }
    }

    void trackChannelDebugInstallation() {
        final JSONObject _properties = new JSONObject();
        addTimeProperty(_properties);
        transformInstallationTaskQueue(new Runnable() {
            @Override
            public void run() {
                try {
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


    /**
     * SDK 全埋点调用方法
     *
     * @param eventName 事件名
     * @param properties 事件属性
     */
    public void trackAutoEvent(final String eventName, final JSONObject properties) {
        //添加 $lib_method 属性
        JSONObject eventProperties = XJDataHelper.appendLibMethodAutoTrack(properties);
        trackInternal(eventName, eventProperties);
    }

    protected void addTimeProperty(JSONObject jsonObject) {
        if (!jsonObject.has("$time")) {
            try {
                jsonObject.put("$time", new Date(System.currentTimeMillis()));
            } catch (JSONException e) {
                XJLog.printStackTrace(e);
            }
        }
    }

    protected boolean isFirstDay(long eventTime) {
        String firstDay = mFirstDay.get();
        if (firstDay == null) {
            return true;
        }
        try {
            if (mIsFirstDayDateFormat == null) {
                mIsFirstDayDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }
            String current = mIsFirstDayDateFormat.format(eventTime);
            return firstDay.equals(current);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return true;
    }

    protected void trackItemEvent(String itemType, String itemId, String eventType, long time, JSONObject properties) {
        try {
            assertKey(itemType);
            assertValue(itemId);
            assertPropertyTypes(properties);

            // 禁用采集事件时，先计算基本信息存储到缓存中
            if (!mXJConfigOptions.isDataCollectEnable) {
                transformItemTaskQueue(itemType, itemId, eventType, time, properties);
                return;
            }

            String eventProject = null;
            if (properties != null && properties.has("$project")) {
                eventProject = (String) properties.get("$project");
                properties.remove("$project");
            }

            JSONObject libProperties = new JSONObject();
            libProperties.put("$lib", "Android");
            libProperties.put("$lib_version", VERSION);
            libProperties.put("$lib_method", "code");

            if (mDeviceInfo.containsKey("$app_version")) {
                libProperties.put("$app_version", mDeviceInfo.get("$app_version"));
            }

            JSONObject superProperties = mSuperProperties.get();
            if (superProperties != null) {
                if (superProperties.has("$app_version")) {
                    libProperties.put("$app_version", superProperties.get("$app_version"));
                }
            }

            StackTraceElement[] trace = (new Exception()).getStackTrace();
            if (trace.length > 1) {
                StackTraceElement traceElement = trace[0];
                String libDetail = String.format("%s##%s##%s##%s", traceElement
                                .getClassName(), traceElement.getMethodName(), traceElement.getFileName(),
                        traceElement.getLineNumber());
                if (!TextUtils.isEmpty(libDetail)) {
                    libProperties.put("$lib_detail", libDetail);
                }
            }

            JSONObject eventProperties = new JSONObject();
            eventProperties.put("item_type", itemType);
            eventProperties.put("item_id", itemId);
            eventProperties.put("type", eventType);
            eventProperties.put("time", time);
            eventProperties.put("properties", TimeUtils.formatDate(properties));
            eventProperties.put("lib", libProperties);

            if (!TextUtils.isEmpty(eventProject)) {
                eventProperties.put("project", eventProject);
            }
            mMessages.enqueueEventMessage(eventType, eventProperties);
            XJLog.i(TAG, "track event11:\n" + JSONUtils.formatJson(eventProperties.toString()));
        } catch (Exception ex) {
            XJLog.printStackTrace(ex);
        }
    }

    protected void trackEvent(final EventType eventType, String eventName, final JSONObject properties, final String
            originalDistinctId) {
        try {
            EventTimer eventTimer = null;
            if (!TextUtils.isEmpty(eventName)) {
                synchronized (mTrackTimer) {
                    eventTimer = mTrackTimer.get(eventName);
                    mTrackTimer.remove(eventName);
                }

                if (eventName.endsWith("_SATimer") && eventName.length() > 45) {// Timer 计时交叉计算拼接的字符串长度 45
                    eventName = eventName.substring(0, eventName.length() - 45);
                }
            }

            if (eventType.isTrack()) {
                assertKey(eventName);
                //如果在线控制禁止了事件，则不触发
                if (mRemoteManager != null && mRemoteManager.ignoreEvent(eventName)) {
                    return;
                }
            }
            if (properties!=null&&!properties.has("attribute")){
                assertPropertyTypes(properties);
            }


            try {
                JSONObject sendProperties;

                if (eventType.isTrack()) {
                    if (mDeviceInfo != null) {
                        sendProperties = new JSONObject(mDeviceInfo);
                    } else {
                        sendProperties = new JSONObject();
                    }

                    //之前可能会因为没有权限无法获取运营商信息，检测再次获取
                    try {
                        if (TextUtils.isEmpty(sendProperties.optString("$carrier")) && mXJConfigOptions.isDataCollectEnable) {
                            String carrier = XJDataUtils.getCarrier(mContext);
                            if (!TextUtils.isEmpty(carrier)) {
                                sendProperties.put("$carrier", carrier);
                            }
                        }
                    } catch (Exception e) {
                        XJLog.printStackTrace(e);
                    }
                    if (!"$AppEnd".equals(eventName)) {
                        //合并 $latest_utm 属性
                        XJDataUtils.mergeJSONObject(ChannelUtils.getLatestUtmProperties(), sendProperties);
                    }
                    mergerDynamicAndSuperProperties(sendProperties);

                    if (mXJConfigOptions.mEnableReferrerTitle && mReferrerScreenTitle != null) {
                        sendProperties.put("$referrer_title", mReferrerScreenTitle);
                    }

                    // 当前网络状况
                    String networkType = NetworkUtils.networkType(mContext);
                    sendProperties.put("$wifi", "WIFI".equals(networkType));
                    sendProperties.put("$network_type", networkType);

                    // GPS
                    try {
                        if (mGPSLocation != null) {
                            sendProperties.put("$latitude", mGPSLocation.getLatitude());
                            sendProperties.put("$longitude", mGPSLocation.getLongitude());
                            sendProperties.put("$country",mGPSLocation.getCountry());
                            sendProperties.put("$province",mGPSLocation.getProvince());
                            sendProperties.put("$city",mGPSLocation.getCity());
                            sendProperties.put("$district",mGPSLocation.getDistrict());
                        }
                    } catch (Exception e) {
                        XJLog.printStackTrace(e);
                    }

                    // 屏幕方向
                    try {
                        String screenOrientation = getScreenOrientation();
                        if (!TextUtils.isEmpty(screenOrientation)) {
                            sendProperties.put("$screen_orientation", screenOrientation);
                        }
                    } catch (Exception e) {
                        XJLog.printStackTrace(e);
                    }
                } else if (eventType.isProfile()) {
                    sendProperties = new JSONObject();
                } else {
                    return;
                }

                // 禁用采集事件时，先计算基本信息存储到缓存中
                if (!mXJConfigOptions.isDataCollectEnable) {
                    if (XJLog.isLogEnabled()) {
                        XJLog.i(TAG, "track event22, isDataCollectEnable = false, eventName = " + eventName + ",property = " + JSONUtils.formatJson(sendProperties.toString()));
                    }
                    transformEventTaskQueue(eventType, eventName, properties, sendProperties, originalDistinctId, getDistinctId(), getLoginId(), eventTimer);
                    return;
                }
                trackEventInternal(eventType, eventName, properties, sendProperties, originalDistinctId, getDistinctId(), getLoginId(), eventTimer);
            } catch (JSONException e) {
                throw new InvalidDataException("Unexpected property");
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    /**
     * 处理 H5 打通的事件
     *
     * @param eventInfo 事件信息
     */
    protected void trackEventH5(String eventInfo) {
        try {
            if (TextUtils.isEmpty(eventInfo)) {
                return;
            }

            // 禁用采集事件时，先计算基本信息存储到缓存中
            if (!mXJConfigOptions.isDataCollectEnable) {
                transformH5TaskQueue(eventInfo);
                return;
            }

            JSONObject eventObject = new JSONObject(eventInfo);
            eventObject.put("_hybrid_h5", true);
            String type = eventObject.getString("type");
            EventType eventType = EventType.valueOf(type.toUpperCase(Locale.getDefault()));

            String distinctIdKey = "distinct_id";
            if (eventType == EventType.TRACK_SIGNUP) {
                eventObject.put("original_id", getAnonymousId());
            } else if (!TextUtils.isEmpty(getLoginId())) {
                eventObject.put(distinctIdKey, getLoginId());
            } else {
                eventObject.put(distinctIdKey, getAnonymousId());
            }
            eventObject.put("anonymous_id", getAnonymousId());
            long eventTime = System.currentTimeMillis();
            eventObject.put("time", eventTime);

            try {
                SecureRandom secureRandom = new SecureRandom();
                eventObject.put("_track_id", secureRandom.nextInt());
            } catch (Exception e) {
                //ignore
            }

            JSONObject propertiesObject = eventObject.optJSONObject("properties");
            // 校验 H5 属性
            assertPropertyTypes(propertiesObject);
            if (propertiesObject == null) {
                propertiesObject = new JSONObject();
            }

            JSONObject libObject = eventObject.optJSONObject("lib");
            if (libObject != null) {
                if (mDeviceInfo.containsKey("$app_version")) {
                    libObject.put("$app_version", mDeviceInfo.get("$app_version"));
                }

                //update lib $app_version from super properties
                JSONObject superProperties = mSuperProperties.get();
                if (superProperties != null) {
                    if (superProperties.has("$app_version")) {
                        libObject.put("$app_version", superProperties.get("$app_version"));
                    }
                }
            }

            if (eventType.isTrack()) {
                if (mDeviceInfo != null) {
                    for (Map.Entry<String, Object> entry : mDeviceInfo.entrySet()) {
                        String key = entry.getKey();
                        if (!TextUtils.isEmpty(key)) {
                            if ("$lib".equals(key) || "$lib_version".equals(key)) {
                                continue;
                            }
                            propertiesObject.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                // 当前网络状况
                String networkType = NetworkUtils.networkType(mContext);
                propertiesObject.put("$wifi", "WIFI".equals(networkType));
                propertiesObject.put("$network_type", networkType);

                // SuperProperties
                mergerDynamicAndSuperProperties(propertiesObject);

                //是否首日访问
                if (eventType.isTrack()) {
                    propertiesObject.put("$is_first_day", isFirstDay(eventTime));
                }
                XJDataUtils.mergeJSONObject(ChannelUtils.getLatestUtmProperties(), propertiesObject);
            }

            if (eventObject.has("_nocache")) {
                eventObject.remove("_nocache");
            }

            if (eventObject.has("server_url")) {
                eventObject.remove("server_url");
            }

            if (eventObject.has("_flush_time")) {
                eventObject.remove("_flush_time");
            }

            if (propertiesObject.has("$project")) {
                eventObject.put("project", propertiesObject.optString("$project"));
                propertiesObject.remove("$project");
            }

            if (propertiesObject.has("$token")) {
                eventObject.put("token", propertiesObject.optString("$token"));
                propertiesObject.remove("$token");
            }

            if (propertiesObject.has("$time")) {
                try {
                    long time = propertiesObject.getLong("$time");
                    if (TimeUtils.isDateValid(time)) {
                        eventObject.put("time", time);
                    }
                } catch (Exception ex) {
                    XJLog.printStackTrace(ex);
                }
                propertiesObject.remove("$time");
            }

            String eventName = eventObject.optString("event");
            if (eventType.isTrack()) {
                // 校验 H5 事件名称
                assertKey(eventName);
                boolean enterDb = isEnterDb(eventName, propertiesObject);
                if (!enterDb) {
                    XJLog.d(TAG, eventName + " event can not enter database");
                    return;
                }

                if (!isTrackEventWithPluginVersion && !propertiesObject.has("$lib_plugin_version")) {
                    JSONArray libPluginVersion = getPluginVersion();
                    if (libPluginVersion == null) {
                        isTrackEventWithPluginVersion = true;
                    } else {
                        try {
                            propertiesObject.put("$lib_plugin_version", libPluginVersion);
                            isTrackEventWithPluginVersion = true;
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                        }
                    }
                }
            }
            eventObject.put("properties", propertiesObject);

            if (eventType == EventType.TRACK_SIGNUP) {
                String loginId = eventObject.getString("distinct_id");
                synchronized (mLoginIdLock) {
                    if (!loginId.equals(DbAdapter.getInstance().getLoginId()) && !loginId.equals(getAnonymousId())) {
                        DbAdapter.getInstance().commitLoginId(loginId);
                        eventObject.put("login_id", loginId);
                        try {
                            if (mEventListenerList != null) {
                                for (XJEventListener eventListener : mEventListenerList) {
                                    eventListener.login();
                                }
                            }
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                        }
                        mMessages.enqueueEventMessage(type, eventObject);
                        if (XJLog.isLogEnabled()) {
                            XJLog.i(TAG, "track event33:\n" + JSONUtils.formatJson(eventObject.toString()));
                        }
                    }
                }
            } else {
                if (!TextUtils.isEmpty(getLoginId())) {
                    eventObject.put("login_id", getLoginId());
                }
                try {
                    if (mEventListenerList != null && eventType.isTrack()) {
                        for (XJEventListener eventListener : mEventListenerList) {
                            eventListener.trackEvent(eventObject);
                        }
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
                mMessages.enqueueEventMessage(type, eventObject);
                if (XJLog.isLogEnabled()) {
                    XJLog.i(TAG, "track event44:\n" + JSONUtils.formatJson(eventObject.toString()));
                }
            }
        } catch (Exception e) {
            //ignore
            XJLog.printStackTrace(e);
        }
    }

    /**
     * 处理渠道相关的事件
     *
     * @param runnable 任务
     */
    protected void transformInstallationTaskQueue(final Runnable runnable) {
        // 禁用采集事件时，先计算基本信息存储到缓存中
        if (!mXJConfigOptions.isDataCollectEnable) {
            mTrackTaskManager.addTrackEventTask(new Runnable() {
                @Override
                public void run() {
                    mTrackTaskManager.transformTaskQueue(runnable);
                }
            });
            return;
        }

        mTrackTaskManager.addTrackEventTask(runnable);
    }

    protected void initSAConfig(String serverURL, String packageName) {
        Bundle configBundle = null;
        try {
            final ApplicationInfo appInfo = mContext.getApplicationContext().getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            configBundle = appInfo.metaData;
        } catch (final PackageManager.NameNotFoundException e) {
            XJLog.printStackTrace(e);
        }

        if (null == configBundle) {
            configBundle = new Bundle();
        }

        if (mXJConfigOptions == null) {
            this.mSDKConfigInit = false;
            mXJConfigOptions = new XJConfigOptions(serverURL);
        } else {
            this.mSDKConfigInit = true;
        }

        if (mXJConfigOptions.mEnableEncrypt) {
            mXJDataEncrypt = new XJDataEncrypt(mContext, mXJConfigOptions.mPersistentSecretKey);
        }

        DbAdapter.getInstance(mContext, packageName, mXJDataEncrypt);
        mTrackTaskManager.setDataCollectEnable(mXJConfigOptions.isDataCollectEnable);

        if (mXJConfigOptions.mInvokeLog) {
            enableLog(mXJConfigOptions.mLogEnabled);
        } else {
            enableLog(configBundle.getBoolean("com.sensorsdata.analytics.android.EnableLogging",
                    this.mDebugMode != XJDataAPI.DebugMode.DEBUG_OFF));
        }

        setServerUrl(serverURL);
        if (mXJConfigOptions.mEnableTrackAppCrash) {
            XJDataExceptionHandler.enableAppCrash();
        }

        if (mXJConfigOptions.mFlushInterval == 0) {
            mXJConfigOptions.setFlushInterval(configBundle.getInt("com.sensorsdata.analytics.android.FlushInterval",
                    15000));
        }

        if (mXJConfigOptions.mFlushBulkSize == 0) {
            mXJConfigOptions.setFlushBulkSize(configBundle.getInt("com.sensorsdata.analytics.android.FlushBulkSize",
                    100));
        }

        if (mXJConfigOptions.mMaxCacheSize == 0) {
            mXJConfigOptions.setMaxCacheSize(32 * 1024 * 1024L);
        }

        if (mXJConfigOptions.isSubProcessFlushData && DbAdapter.getInstance().isFirstProcess()) {
            //如果是首个进程
            DbAdapter.getInstance().commitFirstProcessState(false);
            DbAdapter.getInstance().commitSubProcessFlushState(false);
        }

        this.mAutoTrack = configBundle.getBoolean("com.sensorsdata.analytics.android.AutoTrack",
                false);
        if (mXJConfigOptions.mAutoTrackEventType != 0) {
            enableAutoTrack(mXJConfigOptions.mAutoTrackEventType);
            this.mAutoTrack = true;
        }

        if (!mXJConfigOptions.mInvokeHeatMapEnabled) {
            mXJConfigOptions.mHeatMapEnabled = configBundle.getBoolean("com.sensorsdata.analytics.android.HeatMap",
                    false);
        }

        if (!mXJConfigOptions.mInvokeHeatMapConfirmDialog) {
            mXJConfigOptions.mHeatMapConfirmDialogEnabled = configBundle.getBoolean("com.sensorsdata.analytics.android.EnableHeatMapConfirmDialog",
                    false);
        }

        if (!mXJConfigOptions.mInvokeVisualizedEnabled) {
            mXJConfigOptions.mVisualizedEnabled = configBundle.getBoolean("com.sensorsdata.analytics.android.VisualizedAutoTrack",
                    false);
        }

        if (!mXJConfigOptions.mInvokeVisualizedConfirmDialog) {
            mXJConfigOptions.mVisualizedConfirmDialogEnabled = configBundle.getBoolean("com.sensorsdata.analytics.android.EnableVisualizedAutoTrackConfirmDialog",
                    false);
        }

        enableTrackScreenOrientation(mXJConfigOptions.mTrackScreenOrientationEnabled);

        if (!TextUtils.isEmpty(mXJConfigOptions.mAnonymousId)) {
            identify(mXJConfigOptions.mAnonymousId);
        }

        SHOW_DEBUG_INFO_VIEW = configBundle.getBoolean("com.sensorsdata.analytics.android.ShowDebugInfoView",
                true);

        this.mDisableDefaultRemoteConfig = configBundle.getBoolean("com.sensorsdata.analytics.android.DisableDefaultRemoteConfig",
                false);

        this.mMainProcessName = AppInfoUtils.getMainProcessName(mContext);
        if (TextUtils.isEmpty(this.mMainProcessName)) {
            this.mMainProcessName = configBundle.getString("com.sensorsdata.analytics.android.MainProcessName");
        }
        mIsMainProcess = AppInfoUtils.isMainProcess(mContext, mMainProcessName);

        this.mDisableTrackDeviceId = configBundle.getBoolean("com.sensorsdata.analytics.android.DisableTrackDeviceId",
                false);
        if (isSaveDeepLinkInfo()) {
            ChannelUtils.loadUtmByLocal(mContext);
        } else {
            ChannelUtils.clearLocalUtm(mContext);
        }
//        initSdkWxattribute();
    }

    protected void applySAConfigOptions() {
        if (mXJConfigOptions.mEnableTrackAppCrash) {
            XJDataExceptionHandler.enableAppCrash();
        }

        if (mXJConfigOptions.mAutoTrackEventType != 0) {
            this.mAutoTrack = true;
        }

        if (mXJConfigOptions.mInvokeLog) {
            enableLog(mXJConfigOptions.mLogEnabled);
        }

        enableTrackScreenOrientation(mXJConfigOptions.mTrackScreenOrientationEnabled);

        if (!TextUtils.isEmpty(mXJConfigOptions.mAnonymousId)) {
            identify(mXJConfigOptions.mAnonymousId);
        }
    }

    protected void initSdkWxattribute(){//初始化接口
        track(API_BURY_WXATTRIBUTE);
        flush();
    }

    /**
     * 触发事件的暂停/恢复
     *
     * @param eventName 事件名称
     * @param isPause 设置是否暂停
     */
    protected void trackTimerState(final String eventName, final boolean isPause) {
        final long startTime = SystemClock.elapsedRealtime();
        mTrackTaskManager.addTrackEventTask(new Runnable() {
            @Override
            public void run() {
                try {
                    assertKey(eventName);
                    synchronized (mTrackTimer) {
                        EventTimer eventTimer = mTrackTimer.get(eventName);
                        if (eventTimer != null && eventTimer.isPaused() != isPause) {
                            eventTimer.setTimerState(isPause, startTime);
                        }
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    /**
     * 获取并配置 App 的一些基本属性
     *
     * @return 设备信息
     */
    protected Map<String, Object> setupDeviceInfo() {
        final Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("$lib", "Android");
        deviceInfo.put("$lib_version", VERSION);
        deviceInfo.put("$os", "Android");
        deviceInfo.put("$os_version", DeviceUtils.getOS());
        deviceInfo.put("$manufacturer", DeviceUtils.getManufacturer());
        deviceInfo.put("$model", DeviceUtils.getModel());
        deviceInfo.put("$app_version", AppInfoUtils.getAppVersionName(mContext));
        int[] size = DeviceUtils.getDeviceSize(mContext);
        deviceInfo.put("$screen_width", size[0]);
        deviceInfo.put("$screen_height", size[1]);
        deviceInfo.put("$language",DeviceUtils.getDevicesLange(mContext));
        deviceInfo.put("$ip",DeviceUtils.getIPAddress(mContext));

        String carrier = XJDataUtils.getCarrier(mContext);
        if (!TextUtils.isEmpty(carrier)) {
            deviceInfo.put("$carrier", carrier);
        }

        if (!mDisableTrackDeviceId && !TextUtils.isEmpty(mAndroidId)) {
            deviceInfo.put("$device_id", mAndroidId);
        }

        Integer zone_offset = TimeUtils.getZoneOffset();
        if (zone_offset != null) {
            deviceInfo.put("$timezone_offset", zone_offset);
        }

        deviceInfo.put("$app_id", AppInfoUtils.getProcessName(mContext));
        deviceInfo.put("$app_name", AppInfoUtils.getAppName(mContext));

        return Collections.unmodifiableMap(deviceInfo);
    }

    /**
     * 合并、去重静态公共属性与动态公共属性
     *
     * @param propertiesObject 保存合并后属性的 JSON
     */
    private void mergerDynamicAndSuperProperties(JSONObject propertiesObject) {
        JSONObject superProperties = getSuperProperties();
        JSONObject dynamicSuperProperties = null;
        try {
            if (mDynamicSuperPropertiesCallBack != null) {
                dynamicSuperProperties = mDynamicSuperPropertiesCallBack.getDynamicSuperProperties();
                assertPropertyTypes(dynamicSuperProperties);
            }
        } catch (Exception e) {
            dynamicSuperProperties = null;
            XJLog.printStackTrace(e);
        }
        JSONObject removeDuplicateSuperProperties = XJDataUtils.mergeSuperJSONObject(dynamicSuperProperties, superProperties);
        XJDataUtils.mergeJSONObject(removeDuplicateSuperProperties, propertiesObject);
    }

    private void showDebugModeWarning() {
        try {
            if (mDebugMode == XJDataAPI.DebugMode.DEBUG_OFF) {
                return;
            }
            if (TextUtils.isEmpty(mServerUrl)) {
                return;
            }
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    String info = null;
                    if (mDebugMode == XJDataAPI.DebugMode.DEBUG_ONLY) {
                        info = "现在您打开了 XJData SDK 的 'DEBUG_ONLY' 模式，此模式下只校验数据但不导入数据，数据出错时会以 Toast 的方式提示开发者，请上线前一定使用 DEBUG_OFF 模式。";
                    } else if (mDebugMode == XJDataAPI.DebugMode.DEBUG_AND_TRACK) {
                        info = "现在您打开了 XJData SDK 的 'DEBUG_AND_TRACK' 模式，此模式下校验数据并且导入数据，数据出错时会以 Toast 的方式提示开发者，请上线前一定使用 DEBUG_OFF 模式。";
                    }
                    CharSequence appName = AppInfoUtils.getAppName(mContext);
                    if (!TextUtils.isEmpty(appName)) {
                        info = String.format(Locale.CHINA, "%s：%s", appName, info);
                    }
                    Toast.makeText(mContext, info, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    /**
     * @param eventName 事件名
     * @param eventProperties 事件属性
     * @return 该事件是否入库
     */
    private boolean isEnterDb(String eventName, JSONObject eventProperties) {
        boolean enterDb = true;
        if (mTrackEventCallBack != null) {
            XJLog.d(TAG, "SDK have set trackEvent callBack");
            try {
                enterDb = mTrackEventCallBack.onTrackEvent(eventName, eventProperties);
            } catch (Exception e) {
                XJLog.printStackTrace(e);
            }
            if (enterDb) {
                try {
                    Iterator<String> it = eventProperties.keys();
                    while (it.hasNext()) {
                        String key = it.next();
                        try {
                            assertKey(key);
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                            return false;
                        }
                        Object value = eventProperties.opt(key);
                        if (!(value instanceof CharSequence || value instanceof Number || value
                                instanceof JSONArray || value instanceof Boolean || value instanceof Date)) {
                            XJLog.d(TAG, String.format("The property value must be an instance of " +
                                            "CharSequence/Number/Boolean/JSONArray/Date. [key='%s', value='%s', class='%s']",
                                    key,
                                    value == null ? "" : value.toString(),
                                    value == null ? "" : value.getClass().getCanonicalName()));
                            return false;
                        }

                        if ("app_crashed_reason".equals(key)) {
                            if (value instanceof String && ((String) value).length() > 8191 * 2) {
                                XJLog.d(TAG, "The property value is too long. [key='" + key
                                        + "', value='" + value.toString() + "']");
                                value = ((String) value).substring(0, 8191 * 2) + "$";
                            }
                        } else {
                            if (value instanceof String && ((String) value).length() > 8191) {
                                XJLog.d(TAG, "The property value is too long. [key='" + key
                                        + "', value='" + value.toString() + "']");
                                value = ((String) value).substring(0, 8191) + "$";
                            }
                        }
                        if (value instanceof Date) {
                            eventProperties.put(key, TimeUtils.formatDate((Date) value, Locale.CHINA));
                        } else {
                            eventProperties.put(key, value);
                        }
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        }
        return enterDb;
    }
    private void trackEventInternal(final EventType eventType, final String eventName, final JSONObject properties, final JSONObject sendProperties,
                                    final String originalDistinctId, final String distinctId, final String loginId, final EventTimer eventTimer) throws JSONException {
        String libDetail = null;
        String lib_version = VERSION;
        String app_version = mDeviceInfo.containsKey("$app_version") ? (String) mDeviceInfo.get("$app_version") : "";
        long eventTime = System.currentTimeMillis();
        JSONObject libProperties = new JSONObject();
        if (null != properties) {
            try {
                if (properties.has("$lib_detail")) {
                    libDetail = properties.getString("$lib_detail");
                    properties.remove("$lib_detail");
                }
            } catch (Exception e) {
                XJLog.printStackTrace(e);
            }

            try {
                // 单独处理 $AppStart 和 $AppEnd 的时间戳
                if ("$AppEnd".equals(eventName)) {
                    long appEndTime = properties.optLong("event_time");
                    // 退出时间戳不合法不使用，2000 为打点间隔时间戳
                    if (appEndTime > 2000) {
                        eventTime = appEndTime;
                    }
                    String appEnd_lib_version = properties.optString("$lib_version");
                    String appEnd_app_version = properties.optString("$app_version");
                    if (!TextUtils.isEmpty(appEnd_lib_version)) {
                        lib_version = appEnd_lib_version;
                    } else {
                        properties.remove("$lib_version");
                    }

                    if (!TextUtils.isEmpty(appEnd_app_version)) {
                        app_version = appEnd_app_version;
                    } else {
                        properties.remove("$app_version");
                    }

                    properties.remove("event_time");
                } else if ("$AppStart".equals(eventName)) {
                    long appStartTime = properties.optLong("event_time");
                    if (appStartTime > 0) {
                        eventTime = appStartTime;
                    }
                    properties.remove("event_time");
                }
            } catch (Exception e) {
                XJLog.printStackTrace(e);
            }
            XJDataUtils.mergeJSONObject(properties, sendProperties);
            if (eventType.isTrack()) {
                if ("autoTrack".equals(properties.optString("$lib_method"))) {
                    libProperties.put("$lib_method", "autoTrack");
                } else {
                    libProperties.put("$lib_method", "code");
                    sendProperties.put("$lib_method", "code");
                }
            } else {
                libProperties.put("$lib_method", "code");
            }
        } else {
            libProperties.put("$lib_method", "code");
            if (eventType.isTrack()) {
                sendProperties.put("$lib_method", "code");
            }
        }

        if (null != eventTimer) {
            try {
                double duration = Double.parseDouble(eventTimer.duration());
                if (duration > 0) {
                    sendProperties.put("event_duration", duration);
                }
            } catch (Exception e) {
                XJLog.printStackTrace(e);
            }
        }

        libProperties.put("$lib", "Android");
        libProperties.put("$lib_version", lib_version);
        libProperties.put("$app_version", app_version);


        //update lib $app_version from super properties
        JSONObject superProperties = mSuperProperties.get();
        if (superProperties != null) {
            if (superProperties.has("$app_version")) {
                libProperties.put("$app_version", superProperties.get("$app_version"));
            }
        }

        final JSONObject dataObj = new JSONObject();

        try {
            SecureRandom random = new SecureRandom();
            dataObj.put("_track_id", random.nextInt());
        } catch (Exception e) {
            // ignore
        }

        dataObj.put("time", eventTime);
        dataObj.put("type", eventType.getEventType());

        try {
            if (sendProperties.has("$project")) {
                dataObj.put("project", sendProperties.optString("$project"));
                sendProperties.remove("$project");
            }

            if (sendProperties.has("$token")) {
                dataObj.put("token", sendProperties.optString("$token"));
                sendProperties.remove("$token");
            }

            if (sendProperties.has("$time")) {
                try {
                    Object timeDate = sendProperties.opt("$time");
                    if (timeDate instanceof Date) {
                        if (TimeUtils.isDateValid((Date) timeDate)) {
                            dataObj.put("time", ((Date) timeDate).getTime());
                        }
                    }
                } catch (Exception ex) {
                    XJLog.printStackTrace(ex);
                }
                sendProperties.remove("$time");
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }

        if (TextUtils.isEmpty(distinctId)) {// 如果为空，则说明没有 loginId，所以重新设置当时状态的匿名 Id
            dataObj.put("distinct_id", getAnonymousId());
        } else {
            dataObj.put("distinct_id", distinctId);
        }

        if (!TextUtils.isEmpty(loginId)) {
            dataObj.put("login_id", loginId);
        }
        dataObj.put("anonymous_id", getAnonymousId());
        dataObj.put("lib", libProperties);
        dataObj.put("$appKey",mXJConfigOptions.mAppKey);
        dataObj.put("$quoteId",mXJConfigOptions.mQuoteId);
        if (mXJConfigOptions.email!=null){
            dataObj.put("$email",mXJConfigOptions.email);
        }
        if (mXJConfigOptions.mobile!=null){
            dataObj.put("$mobile",mXJConfigOptions.mobile);
        }
        if (mXJConfigOptions.openid!=null){
            dataObj.put("$openid",mXJConfigOptions.openid);
        }
        if (mXJConfigOptions.unionid!=null){
            dataObj.put("$unionid",mXJConfigOptions.unionid);
        }

        if (eventType == EventType.TRACK) {
            dataObj.put("event", eventName);
            //是否首日访问
            sendProperties.put("$is_first_day", isFirstDay(eventTime));
        } else if (eventType == EventType.TRACK_SIGNUP) {
            dataObj.put("event", eventName);
            dataObj.put("original_id", originalDistinctId);
        }

        if (mAutoTrack && properties != null) {
            if (XJDataAPI.AutoTrackEventType.isAutoTrackType(eventName)) {
                XJDataAPI.AutoTrackEventType trackEventType = XJDataAPI.AutoTrackEventType.autoTrackEventTypeFromEventName(eventName);
                if (trackEventType != null) {
                    if (!isAutoTrackEventTypeIgnored(trackEventType)) {
                        if (properties.has("$screen_name")) {
                            String screenName = properties.getString("$screen_name");
                            if (!TextUtils.isEmpty(screenName)) {
                                String[] screenNameArray = screenName.split("\\|");
                                if (screenNameArray.length > 0) {
                                    libDetail = String.format("%s##%s##%s##%s", screenNameArray[0], "", "", "");
                                }
                            }
                        }
                    }
                }
            }
        }

        if (TextUtils.isEmpty(libDetail)) {
            StackTraceElement[] trace = (new Exception()).getStackTrace();
            if (trace.length > 1) {
                StackTraceElement traceElement = trace[0];
                libDetail = String.format("%s##%s##%s##%s", traceElement
                                .getClassName(), traceElement.getMethodName(), traceElement.getFileName(),
                        traceElement.getLineNumber());
            }
        }

        libProperties.put("$lib_detail", libDetail);

        //防止用户自定义事件以及公共属性可能会加$device_id属性，导致覆盖sdk原始的$device_id属性值
        if (sendProperties.has("$device_id")) {//由于profileSet等类型事件没有$device_id属性，故加此判断
            if (mDeviceInfo.containsKey("$device_id")) {
                sendProperties.put("$device_id", mDeviceInfo.get("$device_id"));
            }
        }
        if (eventType.isTrack()) {
            boolean isEnterDb = isEnterDb(eventName, sendProperties);
            if (!isEnterDb) {
                XJLog.d(TAG, eventName + " event can not enter database");
                return;
            }
            if (!isTrackEventWithPluginVersion && !sendProperties.has("$lib_plugin_version")) {
                JSONArray libPluginVersion = getPluginVersion();
                if (libPluginVersion == null) {
                    isTrackEventWithPluginVersion = true;
                } else {
                    try {
                        sendProperties.put("$lib_plugin_version", libPluginVersion);
                        isTrackEventWithPluginVersion = true;
                    } catch (Exception e) {
                        XJLog.printStackTrace(e);
                    }
                }
            }
        }
        dataObj.put("properties", sendProperties);

        try {
            if (mEventListenerList != null && eventType.isTrack()) {
                for (XJEventListener eventListener : mEventListenerList) {
                    eventListener.trackEvent(dataObj);
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }

        mMessages.enqueueEventMessage(eventType.getEventType(), dataObj);
        if (XJLog.isLogEnabled()) {
            XJLog.i(TAG, "track event55:\n" + JSONUtils.formatJson(dataObj.toString()));
        }
    }

    /**
     * 如果没有授权时，需要将已执行的的缓存队列切换到真正的 TaskQueue 中
     */
    private void transformEventTaskQueue(final EventType eventType, final String eventName, final JSONObject properties, final JSONObject sendProperties,
                                         final String originalDistinctId, final String distinctId, final String loginId, final EventTimer eventTimer) {
        try {
            if (!sendProperties.has("$time") && !("$AppStart".equals(eventName) || "$AppEnd".equals(eventName))) {
                sendProperties.put("$time", new Date(System.currentTimeMillis()));
            }
        } catch (JSONException e) {
            XJLog.printStackTrace(e);
        }
        mTrackTaskManager.transformTaskQueue(new Runnable() {
            @Override
            public void run() {
                try {
                    if (eventType.isTrack()) {
                        JSONObject jsonObject = new JSONObject(mDeviceInfo);
                        JSONUtils.mergeDistinctProperty(jsonObject, sendProperties);
                    }
                    if ("$SignUp".equals(eventName)) {// 如果是 "$SignUp" 则需要重新补上 originalId
                        trackEventInternal(eventType, eventName, properties, sendProperties, getAnonymousId(), distinctId, loginId, eventTimer);
                    } else {
                        trackEventInternal(eventType, eventName, properties, sendProperties, originalDistinctId, distinctId, loginId, eventTimer);
                    }
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    private void transformH5TaskQueue(String eventInfo) {
        try {
            final JSONObject eventObject = new JSONObject(eventInfo);
            JSONObject propertiesObject = eventObject.optJSONObject("properties");
            if (propertiesObject != null && !propertiesObject.has("$time")) {
                propertiesObject.put("$time", System.currentTimeMillis());
            }
            if (XJLog.isLogEnabled()) {
                XJLog.i(TAG, "track H5, isDataCollectEnable = false, eventInfo = " + JSONUtils.formatJson(eventInfo));
            }
            mTrackTaskManager.transformTaskQueue(new Runnable() {
                @Override
                public void run() {
                    try {
                        trackEventH5(eventObject.toString());
                    } catch (Exception e) {
                        XJLog.printStackTrace(e);
                    }
                }
            });
        } catch (Exception ex) {
            XJLog.printStackTrace(ex);
        }
    }

    private void transformItemTaskQueue(final String itemType, final String itemId, final String eventType, final long time, final JSONObject properties) {
        if (XJLog.isLogEnabled()) {
            XJLog.i(TAG, "track item, isDataCollectEnable = false, itemType = " + itemType + ",itemId = " + itemId);
        }
        mTrackTaskManager.transformTaskQueue(new Runnable() {
            @Override
            public void run() {
                try {
                    trackItemEvent(itemType, itemId, eventType, time, properties);
                } catch (Exception e) {
                    XJLog.printStackTrace(e);
                }
            }
        });
    }

    private JSONArray getPluginVersion() {
        try {
            if (!TextUtils.isEmpty(XJDataAPI.ANDROID_PLUGIN_VERSION)) {
                XJLog.i(TAG, "android plugin version: " + XJDataAPI.ANDROID_PLUGIN_VERSION);
                JSONArray libPluginVersion = new JSONArray();
                libPluginVersion.put("android:" + XJDataAPI.ANDROID_PLUGIN_VERSION);
                return libPluginVersion;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return null;
    }

    /**
     * 注册 ContentObserver 监听
     */
    private void registerObserver() {
        // 注册跨进程业务的 ContentObserver 监听
        XJDataContentObserver contentObserver = new XJDataContentObserver();
        ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver.registerContentObserver(DbParams.getInstance().getDataCollectUri(), false, contentObserver);
        contentResolver.registerContentObserver(DbParams.getInstance().getSessionTimeUri(), false, contentObserver);
    }
}
