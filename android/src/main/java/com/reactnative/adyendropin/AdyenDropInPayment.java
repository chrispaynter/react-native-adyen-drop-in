package com.reactnative.adyendropin;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.adyen.checkout.adyen3ds2.Adyen3DS2Component;
import com.adyen.checkout.base.ActionComponentData;
import com.adyen.checkout.base.ComponentError;
import com.adyen.checkout.base.PaymentComponentState;
import com.adyen.checkout.base.component.BaseActionComponent;
import com.adyen.checkout.base.model.PaymentMethodsApiResponse;
import com.adyen.checkout.base.model.paymentmethods.PaymentMethod;
import com.adyen.checkout.base.model.paymentmethods.RecurringDetail;
import com.adyen.checkout.base.model.payments.request.PaymentMethodDetails;
import com.adyen.checkout.base.model.payments.response.Action;
import com.adyen.checkout.base.model.payments.response.QrCodeAction;
import com.adyen.checkout.base.model.payments.response.RedirectAction;
import com.adyen.checkout.base.model.payments.response.Threeds2ChallengeAction;
import com.adyen.checkout.base.model.payments.response.Threeds2FingerprintAction;
import com.adyen.checkout.base.model.payments.response.VoucherAction;
import com.adyen.checkout.card.CardComponent;
import com.adyen.checkout.card.CardConfiguration;
import com.adyen.checkout.core.api.Environment;
import com.adyen.checkout.dropin.DropIn;
import com.adyen.checkout.dropin.DropInConfiguration;
import com.adyen.checkout.dropin.service.CallResult;
import com.adyen.checkout.redirect.RedirectComponent;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class AdyenDropInPayment extends ReactContextBaseJavaModule {
    CardConfiguration cardConfiguration;
    DropInConfiguration dropInConfiguration;
    String publicKey;
    Environment environment;
    String envName;
    boolean isDropIn;
    AdyenDropInPaymentService dropInService = new AdyenDropInPaymentService();
    public static AdyenDropInPayment INSTANCE = null;


    public AdyenDropInPayment(@NonNull ReactApplicationContext reactContext) {
        super(reactContext);
        AdyenDropInPayment.INSTANCE = this;
    }

    public static WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
        WritableMap map = new WritableNativeMap();

        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.putMap(key, convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.putArray(key, convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                map.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                map.putInt(key, (Integer) value);
            } else if (value instanceof Double) {
                map.putDouble(key, (Double) value);
            } else if (value instanceof String) {
                map.putString(key, (String) value);
            } else {
                map.putString(key, value.toString());
            }
        }
        return map;
    }

    public static ReadableArray convertJsonToArray(JSONArray array) throws JSONException {
        WritableNativeArray result = new WritableNativeArray();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONObject) {
                result.pushMap(convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                result.pushArray(convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                result.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                result.pushInt((Integer) value);
            } else if (value instanceof Double) {
                result.pushDouble((Double) value);
            } else if (value instanceof String) {
                result.pushString((String) value);
            } else {
                result.pushString(value.toString());
            }
        }

        return result;

    }

    @ReactMethod
    public void configPayment(String publicKey, String env) {
        this.publicKey = publicKey;
        this.envName = env;
        if (env == null || env.trim().length() <= 0) {
            environment = Environment.TEST;
        } else {
            if (env.equalsIgnoreCase("test")) {
                environment = Environment.TEST;
            } else {
                environment = Environment.EUROPE;
            }
        }

    }

    @ReactMethod
    public void paymentMethods(String paymentMethodsJson) {
        isDropIn = true;
        CardConfiguration cardConfiguration =
                new CardConfiguration.Builder(Locale.getDefault(), environment, publicKey)
                        .build();
        this.cardConfiguration = cardConfiguration;
        Intent resultIntent = new Intent(this.getCurrentActivity(), this.getCurrentActivity().getClass());
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.dropInConfiguration = new DropInConfiguration.Builder(this.getCurrentActivity(), resultIntent, AdyenDropInPaymentService.class).addCardConfiguration(cardConfiguration).build();
        JSONObject jsonObject = null;
        try {
            Log.i("string", paymentMethodsJson);
            jsonObject = new JSONObject(paymentMethodsJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PaymentMethodsApiResponse paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(jsonObject);
        final AdyenDropInPayment adyenDropInPayment = this;
        this.getCurrentActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                DropIn.startPayment(adyenDropInPayment.getCurrentActivity(), paymentMethodsApiResponse, dropInConfiguration);
            }
        });

    }

    @ReactMethod
    public void cardPaymentMethod(String paymentMethodsJson, String name, Boolean showHolderField, Boolean showStoreField) {
        isDropIn = false;
        final AdyenDropInPayment adyenDropInPayment = this;
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(paymentMethodsJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        CardConfiguration cardConfiguration =
                new CardConfiguration.Builder(Locale.getDefault(), environment, publicKey).setHolderNameRequire(showHolderField).setShowStorePaymentField(showStoreField)
                        .build();
        this.cardConfiguration = cardConfiguration;
        PaymentMethodsApiResponse paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(jsonObject);
        final PaymentMethod paymentMethod = adyenDropInPayment.getCardPaymentMethod(paymentMethodsApiResponse, name);
        this.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final CardComponent cardComponent = new CardComponent(paymentMethod, cardConfiguration);
                CardComponentBottomSheet cardComponentDialogFragment = new CardComponentBottomSheet(adyenDropInPayment);
                cardComponentDialogFragment.setPaymentMethod(paymentMethod);
                cardComponentDialogFragment.setCardConfiguration(cardConfiguration);
                cardComponentDialogFragment.setComponent(cardComponent);
                cardComponentDialogFragment.setCancelable(true);
                cardComponentDialogFragment.setShowsDialog(true);
                cardComponentDialogFragment.show(((FragmentActivity) adyenDropInPayment.getCurrentActivity()).getSupportFragmentManager());
            }
        });
    }

    @ReactMethod
    public void storedCardPaymentMethod(String paymentMethodsJson, Integer index) {
        isDropIn = false;
        final AdyenDropInPayment adyenDropInPayment = this;
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(paymentMethodsJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PaymentMethodsApiResponse paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(jsonObject);
        CardConfiguration cardConfiguration =
                new CardConfiguration.Builder(Locale.getDefault(), environment, publicKey)
                        .build();
        this.cardConfiguration = cardConfiguration;
        RecurringDetail paymentMethod = this.getStoredCardPaymentMethod(paymentMethodsApiResponse, index);
        this.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final CardComponent cardComponent = new CardComponent(paymentMethod, cardConfiguration);
                CardComponentBottomSheet cardComponentDialogFragment = new CardComponentBottomSheet(adyenDropInPayment);
                cardComponentDialogFragment.setPaymentMethod(paymentMethod);
                cardComponentDialogFragment.setCardConfiguration(cardConfiguration);
                cardComponentDialogFragment.setComponent(cardComponent);
                cardComponentDialogFragment.setCancelable(true);
                cardComponentDialogFragment.setShowsDialog(true);
                cardComponentDialogFragment.show(((FragmentActivity) adyenDropInPayment.getCurrentActivity()).getSupportFragmentManager());

            }
        });
    }

    @ReactMethod
    public void handleAction(String actionJson) {
        if (isDropIn) {
            CallResult callResult = new CallResult(CallResult.ResultType.ACTION, actionJson);
            dropInService.handleAsyncCallback(callResult);
            return;
        }
        if (actionJson == null || actionJson.length() <= 0) {
            return;
        }
        try {
            BaseActionComponent actionComponent = null;
            Action action = Action.SERIALIZER.deserialize(new JSONObject(actionJson));
            switch (action.getType()) {
                case RedirectAction.ACTION_TYPE:
                    actionComponent = RedirectComponent.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                    actionComponent.handleAction(this.getCurrentActivity(), action);
                    break;
                case Threeds2FingerprintAction.ACTION_TYPE:
                    actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                    actionComponent.handleAction(this.getCurrentActivity(), action);
                    break;
                case Threeds2ChallengeAction.ACTION_TYPE:
                    actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                    actionComponent.handleAction(this.getCurrentActivity(), action);
                    break;
                case QrCodeAction.ACTION_TYPE:
                    actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                    actionComponent.handleAction(this.getCurrentActivity(), action);
                    break;
                case VoucherAction.ACTION_TYPE:
                    actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                    actionComponent.handleAction(this.getCurrentActivity(), action);
                    break;
                default:
                    break;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    @NonNull
    @Override
    public String getName() {
        return AdyenDropInPayment.class.getSimpleName();
    }


    public void handlePaymentSubmit(PaymentComponentState paymentComponentState) {
        if (paymentComponentState.isValid()) {
            WritableMap eventData = new WritableNativeMap();
            WritableMap data = new WritableNativeMap();
            PaymentMethodDetails paymentMethodDetails = paymentComponentState.getData().getPaymentMethod();
            JSONObject jsonObject = PaymentMethodDetails.SERIALIZER.serialize(paymentMethodDetails);
            try {
                WritableMap paymentMethodMap = convertJsonToMap(jsonObject);
                data.putMap("paymentMethod", paymentMethodMap);
                data.putBoolean("storePaymentMethod", paymentComponentState.getData().isStorePaymentMethodEnable());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            eventData.putBoolean("isDropIn", false);
            eventData.putString("env", this.envName);
            eventData.putMap("data", data);
            this.sendEvent(this.getReactApplicationContext(), "onPaymentSubmit", eventData);
        }

    }

    public void handlePaymentProvide(ActionComponentData actionComponentData) {
        WritableMap data = null;
        try {
            data = convertJsonToMap(ActionComponentData.SERIALIZER.serialize(actionComponentData));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        WritableMap resultData = new WritableNativeMap();
        resultData.putBoolean("isDropIn", false);
        resultData.putString("env", this.envName);
        resultData.putString("msg", "");
        resultData.putMap("data", data);
        this.sendEvent(this.getReactApplicationContext(), "onPaymentProvide", resultData);
    }

    void handlePaymentError(ComponentError componentError) {
        WritableMap resultData = new WritableNativeMap();
        resultData.putBoolean("isDropIn", false);
        resultData.putString("env", this.envName);
        resultData.putString("msg", componentError.getErrorMessage());
        resultData.putString("error", componentError.getException().getMessage());
        this.sendEvent(this.getReactApplicationContext(), "onPaymentFail", resultData);
    }

    PaymentMethod getCardPaymentMethod(PaymentMethodsApiResponse
                                               paymentMethodsApiResponse, String name) {
        List<PaymentMethod> paymentMethodList = paymentMethodsApiResponse.getPaymentMethods();
        if (name == null || name.trim().length() <= 0) {
            name = "Credit Card";
        }
        for (PaymentMethod paymentMethod : paymentMethodList) {
            if (paymentMethod.getName().equalsIgnoreCase(name)) {
                return paymentMethod;
            }
        }
        return null;
    }

    RecurringDetail getStoredCardPaymentMethod(PaymentMethodsApiResponse
                                                       paymentMethodsApiResponse, Integer index) {
        List<RecurringDetail> recurringDetailList = paymentMethodsApiResponse.getStoredPaymentMethods();
        if (recurringDetailList == null || recurringDetailList.size() <= 0) {
            return null;
        }
        if (recurringDetailList.size() == 1) {
            return recurringDetailList.get(0);
        }
        return recurringDetailList.get(index);
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
