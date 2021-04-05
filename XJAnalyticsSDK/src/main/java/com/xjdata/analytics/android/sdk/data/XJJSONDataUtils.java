package com.xjdata.analytics.android.sdk.data;

import android.util.Log;

import com.xjdata.analytics.android.sdk.AbstractXJDataAPI;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.API_BURY_WXEVENT;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.PAGE_CHANGE_EVENT;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.TIME_LONG_EVENT;
import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXEVENT_TYPE;
import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXUSER_TYPE;


public class XJJSONDataUtils {
    private static String oldUrl;
    private static String nowUrl;

    public static JSONObject  convertJsonOb(JSONObject eventJson){
        JSONObject object=new JSONObject();
        try {
            String event = eventJson.getString("event");
            if (eventJson.has("anonymous_id")){
                object.put("uuid",eventJson.getString("anonymous_id"));
            }
            object.put("scene","");
            object.put("event",event);
            if (eventJson.has("$appKey")) {
                object.put("appKey", eventJson.getString("$appKey"));
            }
            if (eventJson.has("$email")){
                object.put("email",eventJson.getString("$email"));
            }
            if (eventJson.has("$mobile")){
                object.put("mobile",eventJson.getString("$mobile"));
            }
            if (eventJson.has("$openid")){
                object.put("openid",eventJson.getString("$openid"));
            }
            if (eventJson.has("$unionid")){
                object.put("unionid",eventJson.getString("$unionid"));
            }
            if (eventJson.has("$quoteId")){
                object.put("quoteId",eventJson.getString("$quoteId"));
            }

            JSONObject properties = eventJson.getJSONObject("properties");
            if (properties.has("$language")){//手机首选语言
                object.put("language",properties.getString("$language"));
            }
            if (properties.has("$carrier")){//运营商
                object.put("carrier",properties.getString("$carrier"));
            }
            JSONObject lib = eventJson.getJSONObject("lib");
            if (lib.has("$app_version")){//app版本号
                object.put("appVersion",lib.getString("$app_version"));
            }

            if (properties.has("$network_type")){//网络类型
                object.put("networkType",properties.getString("$network_type"));
            }
            if (properties.has("$ip")){//手机ip
                object.put("ip",properties.getString("$ip"));
            }
            if (properties.has("$model")){//手机产商
                object.put("model",properties.getString("$model"));
            }
            if (properties.has("$os")){//系统类型，android
                object.put("os",properties.getString("$os"));
            }
            if (properties.has("$screen_width")){//宽
                object.put("screen_width",properties.getInt("$screen_width"));
            }
            if (properties.has("$screen_height")){//高
                object.put("screen_height",properties.getInt("$screen_height"));
            }
            if (properties.has("$os_version")){//系统版本
                object.put("osVersion",properties.getString("$os_version"));
            }
            if (lib.has("$lib_version")){//sdk版本
                object.put("libVersion",lib.getString("$lib_version"));
            }
            if (properties.has("$appKey")){
                object.put("appKey",properties.getString("$appKey"));
            }
            object.put("lib","applet");//固定值
            if (properties.has("$country")){
                object.put("country",properties.getString("$country"));// 国家
            }
            if (properties.has("$province")){
                object.put("province",properties.getString("$province"));//省份
            }
            if (properties.has("$city")){
                object.put("city",properties.getString("$$city"));//城市
            }
            if (properties.has("$district")){
                object.put("district",properties.getString("$district"));//区
            }
            if (properties.has("$latitude")){
                object.put("latitude",properties.getString("$latitude"));//经度
            }
            if (properties.has("$longitude")){
                object.put("longitude","$longitude");//纬度
            }




            if (AbstractXJDataAPI.API_BURY_WXSHARE.equals(event)){//分享者信息接口
                if (properties.has("$shareUUid")){
                    object.put("shareUUid",properties.getString("$shareUUid"));
                }
                if (properties.has("$fromSharePageUrl")){
                    object.put("fromSharePageUrl",properties.getString("$fromSharePageUrl"));
                }
                if (properties.has("$fromSharePageName")){
                    object.put("fromSharePageName",properties.getString("$fromSharePageName"));
                }
                if (properties.has("$from")){
                    object.put("from",properties.getString("$from"));
                }
                if (properties.has("$url")){
                    object.put("url",properties.getString("$url"));
                }
            }else if (AbstractXJDataAPI.API_BURY_WXUSER.equals(event)){//上报用户信息接口
                if (properties.has("$shareUUid")){
                    object.put("shareUUid",properties.getString("$shareUUid"));
                }
                if (properties.has("$fromSharePageUrl")){
                    object.put("fromSharePageUrl",properties.getString("$fromSharePageUrl"));
                }
                if (properties.has("$fromSharePageName")){
                    object.put("fromSharePageName",properties.getString("$fromSharePageName"));
                }
                if (properties.has("$systemid")){
                    object.put("systemid",properties.getString("$systemid"));
                }
                if (properties.has("$url")){
                    object.put("url",properties.getString("$url"));
                }
                if (properties.has("$unionid")){
                    object.put("unionid",properties.getString("$unionid"));
                }
                if (properties.has("$openid")){
                    object.put("openid",properties.getString("$openid"));
                }
                if (properties.has("$nickName")){
                    object.put("nickName",properties.getString("$nickName"));
                }
                if (properties.has("$headImgUrl")){
                    object.put("headImgUrl",properties.getString("$headImgUrl"));
                }
                if (properties.has("$mobile")){
                    object.put("mobile",properties.getString("$mobile"));
                }
                if (properties.has("$email")){
                    object.put("email",properties.getString("$email"));
                }
                if (properties.has("$sex")){
                    object.put("sex",properties.getString("$sex"));
                }

            }else if ("$AppViewScreen".equals(event)){
                object.put("type",API_BURY_WXEVENT_TYPE);
                object.put("event",API_BURY_WXEVENT);
                object.put("eventId",PAGE_CHANGE_EVENT);
            JSONObject attribute=new JSONObject();
            if (properties.has("$referrer")){
                oldUrl=properties.getString("$referrer");
                attribute.put("oldUrl",properties.getString("$referrer"));
            }
            if (properties.has("$url")){
                nowUrl=properties.getString("$url");
                attribute.put("nowUrl",properties.getString("$url"));
                }
            object.put("attribute",attribute);
            }else if (API_BURY_WXEVENT.equals(event)){
                if (properties.has("$eventId")){
                    if (properties.getString("$eventId").equals(TIME_LONG_EVENT)){
                        object.put("type",API_BURY_WXEVENT_TYPE);
                        object.put("event",API_BURY_WXEVENT);
                        object.put("url",nowUrl);
                        JSONObject attribute=new JSONObject();
                        attribute.put("time",5);
                        if (nowUrl!=null){
                            attribute.put("currentPage",nowUrl);
                            attribute.put("currentUrl",nowUrl);
                            attribute.put("currentOptions","");
                        }
                        if (oldUrl!=null){
                            attribute.put("lastUrl",oldUrl);
                        }
                        object.put("eventId",TIME_LONG_EVENT);
                        object.put("attribute",attribute);
                    }
                }
            }
            if (properties.has("$type")&&properties.has("attribute")){
                if (API_BURY_WXEVENT_TYPE==properties.getInt("$type")){
                    object.put("type",properties.getInt("$type"));
                  object.putOpt("attribute",properties.getJSONObject("attribute"));
                  object.put("eventId",event);
                }
            }

        }catch (Exception e){
            Log.i("","");
        }
        return object;
    }
//    public JSONArray convertJsonArr(){
//
//    }

}
