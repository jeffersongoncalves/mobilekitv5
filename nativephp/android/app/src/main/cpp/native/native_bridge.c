#include "native_bridge.h"
#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include "../PHP.h"

#define TAG "NativeBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// These are declared in native_bridge.h and defined in php_bridge.c
extern JavaVM *g_jvm;
extern jobject g_bridge_instance;

void NativePHPVibrate(void) {
    LOGI("✅ NativePHPVibrate called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGI("Thread not attached. Attaching...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach thread to JVM");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        if (!cls) {
            LOGE("❌ Failed to get class from g_bridge_instance");
            return;
        }

        // Now call the Kotlin-side nativeVibrate() method (no args)
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeVibrate", "()V");
        if (!mid) {
            LOGE("❌ Failed to find method: nativeVibrate()");
            (*env)->DeleteLocalRef(env, cls);
            return;
        }

        (*env)->CallVoidMethod(env, g_bridge_instance, mid);
        (*env)->DeleteLocalRef(env, cls);
        LOGI("✅ nativeVibrate() method called");
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPShowToast(const char *message) {
    LOGI("✅ NativePHPShowToast called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGI("Thread not attached. Attaching...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeShowToast", "(Ljava/lang/String;)V");
        if (mid) {
            jstring jmsg = (*env)->NewStringUTF(env, message);
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jmsg);
            (*env)->DeleteLocalRef(env, jmsg);
            LOGI("✅ Called nativeShowToast()");
        } else {
            LOGE("❌ nativeShowToast(String) method not found");
        }
        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPShowAlert(
        const char *title,
        const char *message,
        const char **buttonTitles,
        int buttonCount
) {
    LOGI("✅ NativePHPShowAlert called with %d buttons", buttonCount);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGI("Thread not attached. Attaching...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        
        // Create Java string array for buttons
        jclass stringClass = (*env)->FindClass(env, "java/lang/String");
        jobjectArray jbuttonArray = (*env)->NewObjectArray(env, buttonCount, stringClass, NULL);
        
        for (int i = 0; i < buttonCount; i++) {
            jstring jbutton = (*env)->NewStringUTF(env, buttonTitles[i]);
            (*env)->SetObjectArrayElement(env, jbuttonArray, i, jbutton);
            (*env)->DeleteLocalRef(env, jbutton);
        }
        
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeShowAlert",
                                            "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V");
        if (mid) {
            jstring jtitle = (*env)->NewStringUTF(env, title);
            jstring jmessage = (*env)->NewStringUTF(env, message);
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jtitle, jmessage, jbuttonArray);
            (*env)->DeleteLocalRef(env, jtitle);
            (*env)->DeleteLocalRef(env, jmessage);
            (*env)->DeleteLocalRef(env, jbuttonArray);
            LOGI("✅ Called nativeShowAlert() with buttons");
        } else {
            LOGE("❌ nativeShowAlert(String, String, String[]) method not found");
        }
        (*env)->DeleteLocalRef(env, cls);
        (*env)->DeleteLocalRef(env, stringClass);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPShare(const char *title, const char *message) {
    LOGI("✅ NativePHPShare called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGI("Thread not attached. Attaching...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeShare",
                                            "(Ljava/lang/String;Ljava/lang/String;)V");
        if (mid) {
            jstring jtitle = (*env)->NewStringUTF(env, title);
            jstring jmessage = (*env)->NewStringUTF(env, message);

            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jtitle, jmessage);

            (*env)->DeleteLocalRef(env, jtitle);
            (*env)->DeleteLocalRef(env, jmessage);
            LOGI("✅ Called nativeShare()");
        } else {
            LOGE("❌ nativeShare(String, String) method not found");
        }
        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }

}

void NativePHPOpenCamera(void) {
    LOGI("✅ NativePHPOpenCamera called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGI("Thread not attached. Attaching...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeOpenCamera", "()V");
        if (mid) {
            (*env)->CallVoidMethod(env, g_bridge_instance, mid);
            LOGI("✅ Called nativeOpenCamera()");
        } else {
            LOGE("❌ nativeOpenCamera() method not found");
        }
        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPToggleFlashlight(void) {
    LOGI("🌀 NativePHPToggleFlashlight()");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeToggleFlashlight", "()V");

        if (mid) {
            (*env)->CallVoidMethod(env, g_bridge_instance, mid);
            LOGI("✅ Flashlight toggle sent to Kotlin");
        } else {
            LOGE("❌ nativeToggleFlashlight() method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPLocalAuthChallenge(void) {
    LOGI("✅ NativePHPLocalAuthChallenge called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeStartBiometric", "()V");
        if (mid) {
            (*env)->CallVoidMethod(env, g_bridge_instance, mid);
            LOGI("📦 Enqueued 'biometric' native call to PHPBridge");
        } else {
            LOGE("❌ Could not find enqueueNativeCall(String)");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPGetPushToken(void)
{
    LOGI("🚀 NativePHPGetPushToken called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeGetPushToken", "()V");
        if (mid) {
            (*env)->CallVoidMethod(env, g_bridge_instance, mid);
            LOGI("📦 Called nativeGetPushToken() to get FCM token");
        } else {
            LOGE("❌ Could not find nativeGetPushToken()");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPSecureSet(const char *key, const char *value)
{
    LOGI("🔐 NativePHPSecureSet called with key: %s", key);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeSecureSet", "(Ljava/lang/String;Ljava/lang/String;)Z");
        
        if (mid) {
            jstring jkey = (*env)->NewStringUTF(env, key);
            jstring jvalue = (*env)->NewStringUTF(env, value);
            
            jboolean result = (*env)->CallBooleanMethod(env, g_bridge_instance, mid, jkey, jvalue);
            
            (*env)->DeleteLocalRef(env, jkey);
            (*env)->DeleteLocalRef(env, jvalue);
            (*env)->DeleteLocalRef(env, cls);
            
            LOGI("✅ Secure storage set completed with result: %d", result);
        } else {
            LOGE("❌ nativeSecureSet(String, String) method not found");
            (*env)->DeleteLocalRef(env, cls);
        }
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPSecureGet(const char *key, void *return_value)
{
    LOGI("🔓 NativePHPSecureGet called with key: %s", key);

    zval *retval = (zval*)return_value;

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            ZVAL_NULL(retval);
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeSecureGet", "(Ljava/lang/String;)Ljava/lang/String;");
        
        if (mid) {
            jstring jkey = (*env)->NewStringUTF(env, key);
            jstring jresult = (jstring)(*env)->CallObjectMethod(env, g_bridge_instance, mid, jkey);
            
            (*env)->DeleteLocalRef(env, jkey);
            
            if (jresult) {
                const char* cstr = (*env)->GetStringUTFChars(env, jresult, NULL);
                if (cstr) {
                    LOGI("✅ Secure storage get completed, returning: %s", cstr);
                    ZVAL_STRING(retval, cstr);
                    (*env)->ReleaseStringUTFChars(env, jresult, cstr);
                } else {
                    LOGI("⚠️ Failed to get string UTF chars");
                    ZVAL_NULL(retval);
                }
                (*env)->DeleteLocalRef(env, jresult);
            } else {
                LOGI("⚠️ No value returned from Kotlin (null)");
                ZVAL_NULL(retval);
            }
            
            (*env)->DeleteLocalRef(env, cls);
        } else {
            LOGE("❌ nativeSecureGet(String) method not found");
            (*env)->DeleteLocalRef(env, cls);
            ZVAL_NULL(retval);
        }
    } else {
        LOGE("❌ g_bridge_instance is NULL");
        ZVAL_NULL(retval);
    }
}

void NativePHPOpenGallery(const char* media_type, int multiple, int max_items) {
    LOGI("🖼️ NativePHPOpenGallery called with media_type: %s, multiple: %d, max_items: %d", 
         media_type, multiple, max_items);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeOpenGallery", 
                                            "(Ljava/lang/String;ZI)V");
        
        if (mid) {
            jstring jmedia_type = (*env)->NewStringUTF(env, media_type);
            jboolean jmultiple = multiple ? JNI_TRUE : JNI_FALSE;
            jint jmax_items = (jint)max_items;
            
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jmedia_type, jmultiple, jmax_items);
            
            (*env)->DeleteLocalRef(env, jmedia_type);
            (*env)->DeleteLocalRef(env, cls);
            LOGI("✅ Gallery launched via nativeOpenGallery()");
        } else {
            LOGE("❌ nativeOpenGallery(String, boolean, int) method not found");
            (*env)->DeleteLocalRef(env, cls);
        }
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}


void NativePHPInAppBrowser(const char *url) {
    LOGI("🌐 NativePHPInAppBrowser called with: %s", url);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeInAppBrowser", "(Ljava/lang/String;)V");

        if (mid) {
            jstring jurl = (*env)->NewStringUTF(env, url);
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jurl);
            (*env)->DeleteLocalRef(env, jurl);
            LOGI("✅ nativeInAppBrowser() called in Kotlin");
        } else {
            LOGE("❌ nativeInAppBrowser(String) method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPGetLocation(int fine_accuracy) {
    LOGI("📍 NativePHPGetLocation called with fine_accuracy: %d", fine_accuracy);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeGetLocation", "(Z)V");

        if (mid) {
            jboolean jfine_accuracy = fine_accuracy ? JNI_TRUE : JNI_FALSE;
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jfine_accuracy);
            LOGI("✅ nativeGetLocation() called in Kotlin");
        } else {
            LOGE("❌ nativeGetLocation(boolean) method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPCheckLocationPermissions(void) {
    LOGI("🔒 NativePHPCheckLocationPermissions called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeCheckLocationPermissions", "()V");

        if (mid) {
            (*env)->CallVoidMethod(env, g_bridge_instance, mid);
            LOGI("✅ nativeCheckLocationPermissions() called in Kotlin");
        } else {
            LOGE("❌ nativeCheckLocationPermissions() method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPRequestLocationPermissions(void) {
    LOGI("🔒 NativePHPRequestLocationPermissions called");

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeRequestLocationPermissions", "()V");

        if (mid) {
            (*env)->CallVoidMethod(env, g_bridge_instance, mid);
            LOGI("✅ nativeRequestLocationPermissions() called in Kotlin");
        } else {
            LOGE("❌ nativeRequestLocationPermissions() method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPBrowserOpen(const char *url) {
    LOGI("🌐 NativePHPBrowserOpen called with: %s", url);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeBrowserOpen", "(Ljava/lang/String;)V");

        if (mid) {
            jstring jurl = (*env)->NewStringUTF(env, url);
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jurl);
            (*env)->DeleteLocalRef(env, jurl);
            LOGI("✅ nativeBrowserOpen() called in Kotlin");
        } else {
            LOGE("❌ nativeBrowserOpen(String) method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

void NativePHPBrowserOpenAuth(const char *url) {
    LOGI("🔐 NativePHPBrowserOpenAuth called with: %s", url);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("❌ Failed to attach JNI thread");
            return;
        }
    }

    if (g_bridge_instance) {
        jclass cls = (*env)->GetObjectClass(env, g_bridge_instance);
        jmethodID mid = (*env)->GetMethodID(env, cls, "nativeBrowserOpenAuth", "(Ljava/lang/String;)V");

        if (mid) {
            jstring jurl = (*env)->NewStringUTF(env, url);
            (*env)->CallVoidMethod(env, g_bridge_instance, mid, jurl);
            (*env)->DeleteLocalRef(env, jurl);
            LOGI("✅ nativeBrowserOpenAuth() called in Kotlin");
        } else {
            LOGE("❌ nativeBrowserOpenAuth(String) method not found");
        }

        (*env)->DeleteLocalRef(env, cls);
    } else {
        LOGE("❌ g_bridge_instance is NULL");
    }
}

