package ru.qatools.selenoud.util

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.util.concurrent.CountDownLatch

import static java.lang.System.currentTimeMillis

/**
 * @author Ilya Sadykov
 */
@CompileStatic
@InheritConstructors
class TimedCountDownLatch extends CountDownLatch {
    public final long createdTime = currentTimeMillis()
}
