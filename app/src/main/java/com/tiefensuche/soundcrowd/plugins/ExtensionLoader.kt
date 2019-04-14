/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.plugins

import android.content.Context

object ExtensionLoader {

    @Throws(Exception::class)
    fun <T> loadClass(classpath: String, context: Context): T {
        return loadClass("$classpath.Extension", *arrayOf<Any>(context))
    }

    @Throws(Exception::class)
    fun <T> loadClass(classpath: String, vararg args: Any): T {
        val cls = Class.forName(classpath) as Class<T>
        return cls.getConstructor(Context::class.java).newInstance(*args)
    }
}
