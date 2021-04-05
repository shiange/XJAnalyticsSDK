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

package com.xjdata.analytics.android.sdk.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import com.xjdata.analytics.android.sdk.XJConfigOptions;
import com.xjdata.analytics.android.sdk.XJLog;
import com.xjdata.analytics.android.sdk.XJDataAPI;
import com.sensorsdata.analytics.android.sdk.SensorsDataAutoTrackHelper;
import com.xjdata.analytics.android.sdk.ServerUrl;
import com.xjdata.analytics.android.sdk.dialog.XJDataDialogUtils;
import com.xjdata.analytics.android.sdk.remote.BaseXJDataSDKRemoteManager;
import com.xjdata.analytics.android.sdk.remote.XJDataRemoteManagerDebug;

public class XJSchemeHelper {

    private final static String TAG = "SA.SASchemeUtil";

    public static void handleSchemeUrl(Activity activity, Intent intent) {
        if (XJDataAPI.isSDKDisabled()) {
            XJLog.i(TAG, "SDK is disabled,scan code function has been turned off");
            return;
        }
        try {
            Uri uri = null;
            if (activity != null && intent != null) {
                uri = intent.getData();
            }
            if (uri != null) {
                String host = uri.getHost();
                if ("heatmap".equals(host)) {
                    String featureCode = uri.getQueryParameter("feature_code");
                    String postUrl = uri.getQueryParameter("url");
                    if (checkProjectIsValid(postUrl)) {
                        XJDataDialogUtils.showOpenHeatMapDialog(activity, featureCode, postUrl);
                    } else {
                        XJDataDialogUtils.showDialog(activity, "App 集成的项目与电脑浏览器打开的项目不同，无法进行点击分析");
                    }
                    intent.setData(null);
                } else if ("debugmode".equals(host)) {
                    String infoId = uri.getQueryParameter("info_id");
                    String locationHref = uri.getQueryParameter("sf_push_distinct_id");
                    String project = uri.getQueryParameter("project");
                    XJDataDialogUtils.showDebugModeSelectDialog(activity, infoId, locationHref, project);
                    intent.setData(null);
                } else if ("visualized".equals(host)) {
                    String featureCode = uri.getQueryParameter("feature_code");
                    String postUrl = uri.getQueryParameter("url");
                    if (checkProjectIsValid(postUrl)) {
                        XJDataDialogUtils.showOpenVisualizedAutoTrackDialog(activity, featureCode, postUrl);
                    } else {
                        XJDataDialogUtils.showDialog(activity, "App 集成的项目与电脑浏览器打开的项目不同，无法进行可视化全埋点。");
                    }
                    intent.setData(null);
                } else if ("popupwindow".equals(host)) {
                    XJDataDialogUtils.showPopupWindowDialog(activity, uri);
                    intent.setData(null);
                } else if ("encrypt".equals(host)) {
                    String version = uri.getQueryParameter("v");
                    String key = Uri.decode(uri.getQueryParameter("key"));
                    XJLog.d(TAG, "Encrypt, version = " + version + ", key = " + key);
                    String tip;
                    if (TextUtils.isEmpty(version) || TextUtils.isEmpty(key)) {
                        tip = "密钥验证不通过，所选密钥无效";
                    } else if (XJDataAPI.sharedInstance().getSensorsDataEncrypt() != null) {
                        tip = XJDataAPI.sharedInstance().getSensorsDataEncrypt().checkPublicSecretKey(version, key);
                    } else {
                        tip = "当前 App 未开启加密，请开启加密后再试";
                    }
                    Toast.makeText(activity, tip, Toast.LENGTH_LONG).show();
                    intent.setData(null);
                } else if ("channeldebug".equals(host)) {
                    if (ChannelUtils.hasUtmByMetaData(activity)) {
                        XJDataDialogUtils.showDialog(activity, "当前为渠道包，无法使用联调诊断工具");
                        return;
                    }

                    String monitorId = uri.getQueryParameter("monitor_id");
                    if (TextUtils.isEmpty(monitorId)) {
                        return;
                    }
                    String url = XJDataAPI.sharedInstance().getServerUrl();
                    if (TextUtils.isEmpty(url)) {
                        XJDataDialogUtils.showDialog(activity, "数据接收地址错误，无法使用联调诊断工具");
                        return;
                    }
                    ServerUrl serverUrl = new ServerUrl(url);
                    String projectName = uri.getQueryParameter("project_name");
                    if (serverUrl.getProject().equals(projectName)) {
                        String projectId = uri.getQueryParameter("project_id");
                        String accountId = uri.getQueryParameter("account_id");
                        String isReLink = uri.getQueryParameter("is_relink");
                        if ("1".equals(isReLink)) {//续连标识 1 :续连
                            String deviceCode = uri.getQueryParameter("device_code");
                            if (ChannelUtils.checkDeviceInfo(activity, deviceCode)) {//比较设备信息是否匹配
                                SensorsDataAutoTrackHelper.showChannelDebugActiveDialog(activity);
                            } else {
                                XJDataDialogUtils.showDialog(activity, "无法重连，请检查是否更换了联调手机");
                            }
                        } else {
                            XJDataDialogUtils.showChannelDebugDialog(activity, serverUrl.getBaseUrl(), monitorId, projectId, accountId);
                        }
                    } else {
                        XJDataDialogUtils.showDialog(activity, "App 集成的项目与电脑浏览器打开的项目不同，无法使用联调诊断工具");
                    }
                    intent.setData(null);
                } else if ("abtest".equals(host)) {
                    ReflectUtil.callStaticMethod(Class.forName("com.sensorsdata.abtest.core.SensorsABTestSchemeHandler"), "handleSchemeUrl", uri.toString());
                    intent.setData(null);
                } else if ("sensorsdataremoteconfig".equals(host)) {
                    // 开启日志
                    XJDataAPI.sharedInstance().enableLog(true);
                    BaseXJDataSDKRemoteManager sensorsDataSDKRemoteManager = XJDataAPI.sharedInstance().getRemoteManager();
                    // 取消重试
                    if (sensorsDataSDKRemoteManager != null) {
                        sensorsDataSDKRemoteManager.resetPullSDKConfigTimer();
                    }
                    final XJDataRemoteManagerDebug XJDataRemoteManagerDebug =
                            new XJDataRemoteManagerDebug(XJDataAPI.sharedInstance());
                    // 替换为 SensorsDataRemoteManagerDebug 对象
                    XJDataAPI.sharedInstance().setRemoteManager(XJDataRemoteManagerDebug);
                    // 验证远程配置
                    XJLog.i(TAG, "Start debugging remote config");
                    XJDataRemoteManagerDebug.checkRemoteConfig(uri, activity);
                    intent.setData(null);
                } else if ("assistant".equals(host)) {
                    XJConfigOptions configOptions = XJDataAPI.getConfigOptions();
                    if (configOptions != null && configOptions.mDisableDebugAssistant) {
                        return;
                    }
                    String service = uri.getQueryParameter("service");
                    if ("pairingCode".equals(service)) {
                        XJDataDialogUtils.showPairingCodeInputDialog(activity);
                    }
                }
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
    }

    private static boolean checkProjectIsValid(String url) {
        String serverUrl = XJDataAPI.sharedInstance().getServerUrl();
        String sdkProject = null, serverProject = null;
        if (!TextUtils.isEmpty(url)) {
            Uri schemeUri = Uri.parse(url);
            if (schemeUri != null) {
                sdkProject = schemeUri.getQueryParameter("project");
            }
        }
        if (!TextUtils.isEmpty(serverUrl)) {
            Uri serverUri = Uri.parse(serverUrl);
            if (serverUri != null) {
                serverProject = serverUri.getQueryParameter("project");
            }
        }
        return !TextUtils.isEmpty(sdkProject) && !TextUtils.isEmpty(serverProject) && TextUtils.equals(sdkProject, serverProject);
    }
}
