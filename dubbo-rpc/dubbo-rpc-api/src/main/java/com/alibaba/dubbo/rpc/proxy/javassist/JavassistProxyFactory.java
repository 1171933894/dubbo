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
package com.alibaba.dubbo.rpc.proxy.javassist;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.bytecode.Proxy;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.proxy.AbstractProxyFactory;
import com.alibaba.dubbo.rpc.proxy.AbstractProxyInvoker;
import com.alibaba.dubbo.rpc.proxy.InvokerInvocationHandler;

/**
 * JavaassistRpcProxyFactory
 */
public class JavassistProxyFactory extends AbstractProxyFactory {

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

    /**
     * 在 Provider 中，XXXProtocol 会获得被调用的 Exporter 对象，从而获得到 Invoker 对象。但是呢，Invoker
     * 对象实际和 Service 实现对象，是无法直接调用，需要有中间的一层 Wrapper 来代理分发到 Service 对应的方法
     */
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        // Wrapper类不能正确处理带$的类名
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                // 【注意】其中proxy是真实service服务对象
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

}

/*
package com.alibaba.dubbo.common.bytecode;

import com.alibaba.dubbo.demo.provider.DemoDAO;
import com.alibaba.dubbo.demo.provider.DemoServiceImpl;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class Wrapper1
        extends Wrapper
        implements ClassGenerator.DC {
    public static String[] pns;
    public static Map pts;
    public static String[] mns;
    public static String[] dmns;
    public static Class[] mts0;
    public static Class[] mts1;
    public static Class[] mts2;

    public String[] getPropertyNames() {
        return pns;
    }

    public boolean hasProperty(String paramString) {
        return pts.containsKey(paramString);
    }

    public Class getPropertyType(String paramString) {
        return (Class) pts.get(paramString);
    }

    public String[] getMethodNames() {
        return mns;
    }

    public String[] getDeclaredMethodNames() {
        return dmns;
    }

    public void setPropertyValue(Object paramObject1, String paramString, Object paramObject2) {
        DemoServiceImpl w;
        try {
            w = (DemoServiceImpl) paramObject1;
        } catch (Throwable localThrowable) {
            throw new IllegalArgumentException(localThrowable);
        }
        if (paramString.equals("test01")) {
            w.test01 = ((String) paramObject2);
            return;
        }
        if (paramString.equals("demoDAO")) {
            localDemoServiceImpl.setDemoDAO((DemoDAO) paramObject2);
            return;
        }
        throw new NoSuchPropertyException("Not found property \"" + paramString + "\" filed or setter method in class com.alibaba.dubbo.demo.provider.DemoServiceImpl.");
    }

    public Object getPropertyValue(Object paramObject, String paramString) {
        DemoServiceImpl w;
        try {
            w = (DemoServiceImpl) paramObject;
        } catch (Throwable localThrowable) {
            throw new IllegalArgumentException(localThrowable);
        }
        if (paramString.equals("test01")) {
            return localDemoServiceImpl.test01;
        }
        throw new NoSuchPropertyException("Not found property \"" + paramString + "\" filed or setter method in class com.alibaba.dubbo.demo.provider.DemoServiceImpl.");
    }

    public Object invokeMethod(Object paramObject, String paramString, Class[] paramArrayOfClass, Object[] paramArrayOfObject)
            throws InvocationTargetException {
        DemoServiceImpl w;
        try {
            w = (DemoServiceImpl) paramObject;
        } catch (Throwable localThrowable1) {
            throw new IllegalArgumentException(localThrowable1);
        }
        try {
            if ("sayHello".equals(paramString) && paramArrayOfClass.length == 1) {
                return w.sayHello((String) paramArrayOfObject[0]);
            }
            if ("bye".equals(paramString) && paramArrayOfClass.length == 1) {
                w.bye((Object) paramArrayOfObject[0]);
                return null;
            }
            if ("setDemoDAO".equals(paramString) && paramArrayOfClass.length == 1) {
                w.setDemoDAO((DemoDAO) paramArrayOfObject[0]);
                return null;
            }
        } catch (Throwable localThrowable2) {
            throw new InvocationTargetException(localThrowable2);
        }
        throw new NoSuchMethodException("Not found method \"" + paramString + "\" in class com.alibaba.dubbo.demo.provider.DemoServiceImpl.");
    }
}
*/