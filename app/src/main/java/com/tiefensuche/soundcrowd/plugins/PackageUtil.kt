package com.tiefensuche.soundcrowd.plugins

import android.content.Context
import android.content.pm.PackageManager
import java.util.*

object PackageUtil {

    fun getPackageContext(context: Context, packageName: String): Context? {
        return try {
            context.applicationContext.createPackageContext(packageName,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun getAppsByPrefix(context: Context, prefix: String): List<String> {
        val packages = ArrayList<String>()
        for (applicationInfo in context.packageManager.getInstalledApplications(0)) {
            if (applicationInfo.packageName.startsWith(prefix)) {
                packages.add(applicationInfo.packageName)
            }
        }
        return packages
    }
}
