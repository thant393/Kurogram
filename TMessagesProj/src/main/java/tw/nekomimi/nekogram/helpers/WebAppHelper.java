package tw.nekomimi.nekogram.helpers;

import android.app.Activity;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.web.BotWebViewContainer;

import java.util.function.Consumer;

import tw.nekomimi.nekogram.KuroConfig;

public class WebAppHelper {

    private static JsonObject warpInEvent(String event, JsonObject data) {
        var callback = new JsonObject();
        callback.addProperty("event", event);
        if (data != null) {
            callback.add("data", data);
        }
        return callback;
    }

    public static void processBotEvents(BotWebViewContainer.Delegate delegate, String eventData, Consumer<String> eventCallback) {
        var element = JsonParser.parseString(eventData);
        if (!element.isJsonObject()) {
            return;
        }
        var eventObject = element.getAsJsonObject();
        if (!eventObject.has("event")) {
            return;
        }
        var event = eventObject.get("event").getAsString();
        if (event.equals("get_config")) {
            var data = new JsonObject();
            data.addProperty("trust", !KuroConfig.shouldNOTTrustMe);
            eventCallback.accept(warpInEvent("config", data).toString());
        } else if (event.equals("set_config")) {
            var data = eventObject.get("data").getAsJsonObject();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("kuroconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            switch (data.get("key").getAsString()) {
                case "trust":
                    KuroConfig.shouldNOTTrustMe = !data.get("value").getAsBoolean();
                    editor.putBoolean("shouldNOTTrustMe", KuroConfig.shouldNOTTrustMe);
                    break;
            }
            editor.apply();
        }
    }
}
