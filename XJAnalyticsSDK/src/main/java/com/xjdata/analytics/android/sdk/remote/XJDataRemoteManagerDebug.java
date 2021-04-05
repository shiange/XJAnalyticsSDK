/*
 * Created by yuejianzhong on 2020/11/04.
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

package com.xjdata.analytics.android.sdk.remote;

import android.app.Activity;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.TextUtils;

import com.xjdata.analytics.android.sdk.XJLog;
import com.xjdata.analytics.android.sdk.XJDataAPI;
import com.xjdata.analytics.android.sdk.ServerUrl;
import com.xjdata.analytics.android.sdk.dialog.XJDataDialogUtils;
import com.xjdata.analytics.android.sdk.dialog.XJDataLoadingDialog;
import com.xjdata.analytics.android.sdk.network.HttpCallback;
import com.xjdata.analytics.android.sdk.util.AppInfoUtils;
import com.xjdata.analytics.android.sdk.util.NetworkUtils;

import org.json.JSONObject;

/**
 * SDK 调试采集控制时，调试管理类
 */
public class XJDataRemoteManagerDebug extends BaseXJDataSDKRemoteManager {

    private static final String TAG = "SA.SensorsDataRemoteManagerDebug";
    private String errorMsg = "";

    public XJDataRemoteManagerDebug(XJDataAPI sensorsDataAPI) {
        super(sensorsDataAPI);
        XJLog.i(TAG, "remote config: Construct a SensorsDataRemoteManagerDebug");
    }

    @Override
    public void pullSDKConfigFromServer() {
        XJLog.i(TAG, "remote config: Running pullSDKConfigFromServer");
    }

    @Override
    public void requestRemoteConfig(RandomTimeType randomTimeType, boolean enableConfigV) {
        XJLog.i(TAG, "remote config: Running requestRemoteConfig");
    }

    @Override
    public void resetPullSDKConfigTimer() {
        XJLog.i(TAG, "remote config: Running resetPullSDKConfigTimer");
    }

    @Override
    public void applySDKConfigFromCache() {
        XJLog.i(TAG, "remote config: Running applySDKConfigFromCache");
    }

    @Override
    public void setSDKRemoteConfig(XJDataSDKRemoteConfig sdkRemoteConfig) {
        try {
            JSONObject eventProperties = new JSONObject();
            JSONObject remoteConfigJson = sdkRemoteConfig.toJson().put("debug", true);
            String remoteConfigString = remoteConfigJson.toString();
            eventProperties.put("$app_remote_config", remoteConfigString);
            XJDataAPI.sharedInstance().trackInternal("$AppRemoteConfigChanged", eventProperties);
            XJDataAPI.sharedInstance().flushSync();
            mSDKRemoteConfig = sdkRemoteConfig;
            XJLog.i(TAG, "remote config: The remote configuration takes effect immediately");
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    /**
     * 校验采集配置
     *
     * @param uri 扫码 uri
     * @param activity activity
     */
    public void checkRemoteConfig(final Uri uri, final Activity activity) {
        if (verifyRemoteRequestParameter(uri, activity)) {
            XJDataDialogUtils.showDialog(activity, "提示",
                    "开始获取采集控制信息", "继续", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final XJDataLoadingDialog loadingDialog = new XJDataLoadingDialog(activity);
                            loadingDialog.show();
                            // 发起请求
                            requestRemoteConfig(false, new HttpCallback.StringCallback() {
                                @Override
                                public void onFailure(int code, String errorMessage) {
                                    loadingDialog.dismiss();
                                    XJDataDialogUtils.showDialog(activity, "远程配置获取失败，请稍后重新扫描二维码");
                                    XJLog.i(TAG, "remote config: Remote request was failed,code is " + code +
                                            ",errorMessage is" + errorMessage);
                                }

                                @Override
                                public void onResponse(String response) {
                                    loadingDialog.dismiss();
                                    if (!TextUtils.isEmpty(response)) {
                                        XJDataSDKRemoteConfig sdkRemoteConfig = toSDKRemoteConfig(response);
                                        String nv = uri.getQueryParameter("nv");
                                        if (!sdkRemoteConfig.getNewVersion().equals(nv)) {
                                            XJDataDialogUtils.showDialog(activity, "信息版本不一致", "获取到采集控制信息的版本：" +
                                                            sdkRemoteConfig.getNewVersion() +
                                                            "，二维码信息的版本：" + nv + "，请稍后重新扫描二维码", "确认",
                                                    null, null, null).show();
                                        } else {
                                            XJDataDialogUtils.showDialog(activity, "采集控制加载完成，可以通过 Android Studio 控制台日志来调试");
                                            setSDKRemoteConfig(sdkRemoteConfig);
                                        }
                                    } else {
                                        XJDataDialogUtils.showDialog(activity, "远程配置获取失败，请稍后再试");
                                    }
                                    XJLog.i(TAG, "remote config: Remote request was successful,response data is " + response);
                                }

                                @Override
                                public void onAfter() {

                                }
                            });
                        }
                    }, "取消", null).show();
        } else {
            // 没有校验通过
            XJDataDialogUtils.showDialog(activity, errorMsg);
        }
    }

    /**
     * 校验本地网络配置，及 uri 中参数和本地参数
     *
     * @param uri 扫码唤醒时的 uri
     * @param activity activity
     * @return 是否校验通过
     */
    private boolean verifyRemoteRequestParameter(Uri uri, Activity activity) {
        boolean isVerify = false;
        String appId = uri.getQueryParameter("app_id");
        String os = uri.getQueryParameter("os");
        String project = uri.getQueryParameter("project");
        String nv = uri.getQueryParameter("nv");
        String localProject = "";
        String serverUrl = mSensorsDataAPI.getServerUrl();
        if (!TextUtils.isEmpty(serverUrl)) {
            localProject = new ServerUrl(serverUrl).getProject();
        }
        XJLog.i(TAG, "remote config: ServerUrl is " + serverUrl);
        if (!NetworkUtils.isNetworkAvailable(mContext)) {
            errorMsg = "网络连接失败，请检查设备网络，确认网络畅通后，请重新扫描二维码进行调试";
        } else if (mSensorsDataAPI != null && !mSensorsDataAPI.isNetworkRequestEnable()) {
            errorMsg = "SDK 网络权限已关闭，请允许 SDK 访问网络";
            XJLog.i(TAG, "enableNetworkRequest is false");
        } else if (mDisableDefaultRemoteConfig) {
            errorMsg = "采集控制网络权限已关闭，请允许采集控制访问网络";
            XJLog.i(TAG, "disableDefaultRemoteConfig is true");
        } else if (!localProject.equals(project)) {
            errorMsg = "App 集成的项目与二维码对应的项目不同，无法进行调试";
        } else if (!"Android".equals(os)) {
            errorMsg = "App 与二维码对应的操作系统不同，无法进行调试";
        } else if (!AppInfoUtils.getProcessName(activity).equals(appId)) {
            errorMsg = "当前 App 与二维码对应的 App 不同，无法进行调试";
        } else if (TextUtils.isEmpty(nv)) {
            errorMsg = "二维码信息校验失败，请检查采集控制是否配置正确";
        } else {
            isVerify = true;
        }
        XJLog.i(TAG, "remote config: Uri is " + uri.toString());
        XJLog.i(TAG, "remote config: The verification result is " + isVerify);
        return isVerify;
    }
}
