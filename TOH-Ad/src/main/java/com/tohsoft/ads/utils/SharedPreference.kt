@file:Suppress("unused")

package com.tohsoft.ads.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object SharedPreference {

    var sharedPreferences: SharedPreferences? = null

    private fun getSharedPreferences(context: Context?): SharedPreferences? {
        if (context == null) {
            return null
        }
        try {
            sharedPreferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
        return sharedPreferences
    }

    // String
    fun getString(context: Context?, key: Any?, defaultValue: String?): String? {
        if (context != null) {
            try {
                return getSharedPreferences(context)!!.getString(key.toString(), defaultValue)
            } catch (e: Exception) {
                AdDebugLog.loge(e)
            }
        }
        return defaultValue
    }

    fun setString(context: Context?, key: Any?, data: String?) {
        try {
            if (context != null) {
                getSharedPreferences(context)!!.edit {
                    putString(key.toString(), data)
                }
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
    }

    // Long
    fun getLong(context: Context?, key: Any?, defaultValue: Long): Long {
        if (context != null) {
            try {
                return getSharedPreferences(context)!!.getLong(key.toString(), defaultValue)
            } catch (e: Exception) {
                AdDebugLog.loge(e)
            }
        }
        return defaultValue
    }

    fun setLong(context: Context?, key: Any?, data: Long) {
        if (context != null) {
            try {
                getSharedPreferences(context)!!.edit {
                    putLong(key.toString(), data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Boolean
    fun getBoolean(context: Context?, key: Any?, defaultValue: Boolean): Boolean {
        if (context != null) {
            try {
                return getSharedPreferences(context)!!.getBoolean(key.toString(), defaultValue)
            } catch (e: Exception) {
                AdDebugLog.loge(e)
            }
        }
        return defaultValue
    }

    fun setBoolean(context: Context?, key: Any?, data: Boolean) {
        if (context != null) {
            try {
                getSharedPreferences(context)!!.edit {
                    putBoolean(key.toString(), data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Integer
    fun getInt(context: Context?, key: Any?, defaultValue: Int): Int {
        if (context != null) {
            try {
                return getSharedPreferences(context)!!.getInt(key.toString(), defaultValue)
            } catch (e: Exception) {
                AdDebugLog.loge(e)
            }
        }
        return defaultValue
    }

    fun setInt(context: Context?, key: Any?, data: Int) {
        try {
            if (context != null) {
                getSharedPreferences(context)!!.edit {
                    putInt(key.toString(), data)
                }
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
    }

    // Float
    fun getFloat(context: Context?, key: Any?, defaultValue: Float): Float {
        if (context != null) {
            try {
                return getSharedPreferences(context)!!.getFloat(key.toString(), defaultValue)
            } catch (e: Exception) {
                AdDebugLog.loge(e)
                return defaultValue
            }
        }
        return defaultValue
    }

    fun setFloat(context: Context?, key: Any?, data: Float) {
        try {
            if (context != null) {
                getSharedPreferences(context)!!.edit {
                    putFloat(key.toString(), data)
                }
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
    }
}