package com.babariviere.sms;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.babariviere.sms.permisions.Permissions;
import com.babariviere.sms.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

class SimCardsHandler implements PluginRegistry.RequestPermissionsResultListener {
    private final String[] permissionsList = new String[]{Manifest.permission.READ_PHONE_STATE};
    private PluginRegistry.Registrar registrar;
    private MethodChannel.Result result;
    static Context context;
    String TAG ="SimCardsHandler";

    SimCardsHandler(PluginRegistry.Registrar registrar, MethodChannel.Result result) {
        this.registrar = registrar;
        this.result = result;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != Permissions.READ_PHONE_STATE) {
            return false;
        }
        boolean isOk = true;
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                isOk = false;
                break;
            }
        }
        if (isOk) {
            getSimCards();
            return true;
        }
        result.error("#01", "permission denied", null);
        return false;
    }

    void handle(Permissions permissions) {
        if (permissions.checkAndRequestPermission(permissionsList, Permissions.READ_PHONE_STATE)) {
            getSimCards();
        }
    }

    private void getSimCards() {
        context = registrar.context();
        JSONArray simCards = new JSONArray();
        Integer activeSubscriptionInfoCount = null;
        Integer activeSubscriptionInfoCountMax = null;

        try {
            TelephonyManager telephonyManager = new TelephonyManager(context);
            int phoneCount = telephonyManager.getSimCount();
            Log.d(TAG, "Phone Count: " + phoneCount);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                activeSubscriptionInfoCount = subscriptionManager != null ? subscriptionManager.getActiveSubscriptionInfoCount() : 0;
                activeSubscriptionInfoCountMax = subscriptionManager != null ? subscriptionManager.getActiveSubscriptionInfoCountMax() : 0;

                List<SubscriptionInfo> subscriptionCardsInfo = subscriptionManager.getActiveSubscriptionInfoList();
                Log.d(TAG, "Cards info: " + subscriptionCardsInfo.size() );
                for (SubscriptionInfo subscriptionInfo : subscriptionCardsInfo) {
                    JSONObject simCard = new JSONObject();
                    int simSlotIndex = subscriptionInfo.getSimSlotIndex();
                    simCard.put("slot", simSlotIndex);
                    simCard.put("carrierName", subscriptionInfo.getCarrierName().toString());
                    simCard.put("countryCode", subscriptionInfo.getCountryIso());
                    simCard.put("dataRoaming", subscriptionInfo.getDataRoaming()); // 1 is enabled ; 0 is disabled
                    simCard.put("displayName", subscriptionInfo.getDisplayName().toString());
                    simCard.put("serialNumber", subscriptionInfo.getIccId());
                    simCard.put("mcc", subscriptionInfo.getMcc());
                    simCard.put("mnc", subscriptionInfo.getMnc());
                    simCard.put("phoneNumber", subscriptionInfo.getNumber());
                    simCard.put("isNetworkRoaming", subscriptionManager.isNetworkRoaming(simSlotIndex));
                    simCard.put("subscriptionId", subscriptionInfo.getSubscriptionId());
                    simCard.put("imei", telephonyManager.getSimId(simSlotIndex));
                    simCard.put("state", telephonyManager.getSimState(simSlotIndex));

                    simCards.put(simCard);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            result.error("2", e.getMessage(), null);
            return;
        }

        result.success(simCards);
    }
}

class SimCardsProvider implements MethodChannel.MethodCallHandler {
    private final Permissions permissions;
    private final PluginRegistry.Registrar registrar;

    SimCardsProvider(PluginRegistry.Registrar registrar) {
        this.registrar = registrar;
        permissions = new Permissions(registrar.activity());
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (!call.method.equals("getSimCards")) {
            result.notImplemented();
        } else {
            getSimCards(result);
        }
    }

    private void getSimCards(MethodChannel.Result result) {
        SimCardsHandler handler = new SimCardsHandler(registrar, result);
        this.registrar.addRequestPermissionsResultListener(handler);
        handler.handle(permissions);
    }
}