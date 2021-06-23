/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.common.utils;

import java.io.OutputStream;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class JVMUtil {
    public static void jstack(OutputStream stream) throws Exception {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMxBean.dumpAllThreads(true, true)) {
            stream.write(getThreadDumpString(threadInfo).getBytes());
        }
    }

    private static String getThreadDumpString(ThreadInfo threadInfo) {
        StringBuilder sb = new StringBuilder("\"" + threadInfo.getThreadName() + "\"" +
                " Id=" + threadInfo.getThreadId() + " " +
                threadInfo.getThreadState());
        if (threadInfo.getLockName() != null) {
            sb.append(" on " + threadInfo.getLockName());
        }
        if (threadInfo.getLockOwnerName() != null) {
            sb.append(" owned by \"" + threadInfo.getLockOwnerName() +
                    "\" Id=" + threadInfo.getLockOwnerId());
        }
        if (threadInfo.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (threadInfo.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;

        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        MonitorInfo[] lockedMonitors = threadInfo.getLockedMonitors();
        for (; i < stackTrace.length && i < 32; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && threadInfo.getLockInfo() != null) {
                Thread.State ts = threadInfo.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : lockedMonitors) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
        }
        if (i < stackTrace.length) {
            sb.append("\t...");
            sb.append('\n');
        }

        LockInfo[] locks = threadInfo.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- " + li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }
}

/**
 * 123312:tmp yunai$ cat Dubbo_JStack.log.2018-03-27_18\:57\:32
 * "pool-2-thread-1" Id=11 RUNNABLE
 * 	at sun.management.ThreadImpl.dumpThreads0(Native Method)
 * 	at sun.management.ThreadImpl.dumpAllThreads(ThreadImpl.java:454)
 * 	at com.alibaba.dubbo.common.utils.JVMUtil.jstack(JVMUtil.java:34)
 * 	at com.alibaba.dubbo.common.threadpool.support.AbortPolicyWithReport$1.run(AbortPolicyWithReport.java:122)
 * 	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
 * 	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
 * 	at java.lang.Thread.run(Thread.java:745)
 *
 * 	Number of locked synchronizers = 1
 * 	- java.util.concurrent.ThreadPoolExecutor$Worker@5cbc508c
 *
 * "Monitor Ctrl-Break" Id=5 RUNNABLE (in native)
 * 	at java.net.SocketInputStream.socketRead0(Native Method)
 * 	at java.net.SocketInputStream.socketRead(SocketInputStream.java:116)
 * 	at java.net.SocketInputStream.read(SocketInputStream.java:171)
 * 	at java.net.SocketInputStream.read(SocketInputStream.java:141)
 * 	at sun.nio.cs.StreamDecoder.readBytes(StreamDecoder.java:284)
 * 	at sun.nio.cs.StreamDecoder.implRead(StreamDecoder.java:326)
 * 	at sun.nio.cs.StreamDecoder.read(StreamDecoder.java:178)
 * 	-  locked java.io.InputStreamReader@5c7efb52
 * 	at java.io.InputStreamReader.read(InputStreamReader.java:184)
 * 	at java.io.BufferedReader.fill(BufferedReader.java:161)
 * 	at java.io.BufferedReader.readLine(BufferedReader.java:324)
 * 	-  locked java.io.InputStreamReader@5c7efb52
 * 	at java.io.BufferedReader.readLine(BufferedReader.java:389)
 * 	at com.intellij.rt.execution.application.AppMainV2$1.run(AppMainV2.java:64)
 *
 * "Signal Dispatcher" Id=4 RUNNABLE
 *
 * "Finalizer" Id=3 WAITING on java.lang.ref.ReferenceQueue$Lock@197c6eb9
 * 	at java.lang.Object.wait(Native Method)
 * 	-  waiting on java.lang.ref.ReferenceQueue$Lock@197c6eb9
 * 	at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:143)
 * 	at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:164)
 * 	at java.lang.ref.Finalizer$FinalizerThread.run(Finalizer.java:209)
 *
 * "Reference Handler" Id=2 WAITING on java.lang.ref.Reference$Lock@7b19fa34
 * 	at java.lang.Object.wait(Native Method)
 * 	-  waiting on java.lang.ref.Reference$Lock@7b19fa34
 * 	at java.lang.Object.wait(Object.java:502)
 * 	at java.lang.ref.Reference.tryHandlePending(Reference.java:191)
 * 	at java.lang.ref.Reference$ReferenceHandler.run(Reference.java:153)
 *
 * "main" Id=1 TIMED_WAITING
 * 	at java.lang.Thread.sleep(Native Method)
 * 	at com.alibaba.dubbo.common.threadpool.AbortPolicyWithReportTest.jStackDumpTest(AbortPolicyWithReportTest.java:44)
 * 	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 * 	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
 * 	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
 * 	at java.lang.reflect.Method.invoke(Method.java:498)
 * 	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
 * 	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
 * 	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
 * 	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
 * 	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
 * 	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
 * 	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
 * 	at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
 * 	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
 * 	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
 * 	at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
 * 	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
 * 	at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
 * 	at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
 * 	at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)
 * 	at com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:47)
 * 	at com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)
 * 	at com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)
 */
