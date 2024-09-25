package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.util.Log
import com.tonapps.tonkeeper.manager.tonconnect.ConnectRequest
import org.json.JSONArray
import org.json.JSONObject
import uikit.widget.webview.bridge.JsBridge
import uikit.widget.webview.bridge.message.BridgeMessage

class DAppBridge(
    val deviceInfo: String,
    val isWalletBrowser: Boolean = true,
    val protocolVersion: Int = 2,
    val send: suspend (array: JSONArray) -> JSONObject,
    val connect: suspend (protocolVersion: Int, request: ConnectRequest) -> JSONObject,
    val restoreConnection: suspend () -> JSONObject,
    val disconnect: suspend () -> Unit,
    val tonapiPost: suspend (method: String, params: String) -> String,
    val tonapiGet: suspend (method: String, params: String) -> String,
): JsBridge("tonkeeper") {

    override val availableFunctions = arrayOf("send", "connect", "restoreConnection", "disconnect")

    init {
        keys["deviceInfo"] = deviceInfo
        keys["protocolVersion"] = protocolVersion
        keys["isWalletBrowser"] = isWalletBrowser
    }

    override suspend fun invokeFunction(name: String, args: JSONArray): Any? {
        return when (name) {
            "connect" -> connect(protocolVersion, ConnectRequest.parse(args.getJSONObject(1))).toString()
            "send" -> send(args).toString()
            "restoreConnection" -> restoreConnection().toString()
            "disconnect" -> disconnect()
            "tonapi.get" -> tonapiGet(args.getString(0), args.optString(1) ?: "")
            "tonapi.post" -> tonapiPost(args.getString(0), args.optString(1) ?: "")
            else -> null
        }
    }

    override fun jsInjection(): String {
        val funcs = availableFunctions.joinToString(",") {"""
            $it: (...args) => {
                return new Promise((resolve, reject) => window.invokeRnFunc('${it}', args, resolve, reject))
            }
        """}.replace("\n", "").replace("  ", "")

        return """
            (() => {
                if (!window.${windowKey}) {
                    window.rnPromises = {};
                    window.rnEventListeners = [];
                    window.invokeRnFunc = (name, args, resolve, reject) => {
                        const invocationId = btoa(Math.random()).substring(0, 12);
                        const timeoutMs = ${timeout};
                        const timeoutId = timeoutMs ? setTimeout(() => reject(new Error('bridge timeout for function with name: '+name+'')), timeoutMs) : null;
                        window.rnPromises[invocationId] = { resolve, reject, timeoutId }
                        window.ReactNativeWebView.postMessage(JSON.stringify({
                            type: '${BridgeMessage.Type.InvokeRnFunc.value}',
                            invocationId: invocationId,
                            name,
                            args,
                        }));
                    };
                    
                    window.addEventListener('message', ({ data }) => {
                        try {
                            const message = data;
                            console.log('message bridge', JSON.stringify(message));
                            if (message.type === '${BridgeMessage.Type.FunctionResponse.value}') {
                                const promise = window.rnPromises[message.invocationId];
                                
                                if (!promise) {
                                    return;
                                }
                                
                                if (promise.timeoutId) {
                                    clearTimeout(promise.timeoutId);
                                }
                                
                                if (message.status === 'fulfilled') {
                                    promise.resolve(JSON.parse(message.data));
                                } else {
                                    promise.reject(new Error(message.data));
                                }
                                
                                delete window.rnPromises[message.invocationId];
                            }
                            
                            if (message.type === '${BridgeMessage.Type.Event.value}') {
                                window.rnEventListeners.forEach((listener) => listener(message.event));
                            }
                        } catch { }
                    });
                }
                
                const listen = (cb) => {
                    window.rnEventListeners.push(cb);
                    return () => {
                        const index = window.rnEventListeners.indexOf(cb);
                        if (index > -1) {
                            window.rnEventListeners.splice(index, 1);
                        }
                    };
                };
                
                window.${windowKey} = {
                    tonconnect: Object.assign(${JSONObject(keys)},{ $funcs },{ listen }),
                    tonapi: {
                        get: (method, params) => {
                            if (!method) {
                                return Promise.reject(new Error('method is required'));
                            }
                            return new Promise((resolve, reject) => window.invokeRnFunc('tonapi.get', [method, params], resolve, reject))
                        },
                        post: (method, params) => {
                            if (!method) {
                                return Promise.reject(new Error('method is required'));
                            }
                            return new Promise((resolve, reject) => window.invokeRnFunc('tonapi.post', [method, params], resolve, reject))
                        },
                    },
                }
            })();
        """
    }

}