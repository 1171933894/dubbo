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
package com.alibaba.dubbo.rpc.proxy.wrapper;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.service.GenericService;

import java.lang.reflect.Constructor;

/**
 * StubProxyFactoryWrapper
 *
 * 【dubbo的本地存根的原理】
 * 远程服务后，客户端通常只剩下接口，而实现全在服务器端，但提供方有些时候想在客户端也执行部分逻辑，那么就在服务
 * 消费者这一端提供了一个Stub类，然后当消费者调用provider方提供的dubbo服务时，客户端生成 Proxy 实例，这个Proxy实例就是我们正常调用dubbo
 * 远程服务要生成的代理实例，然后消费者这方会把 Proxy 通过构造函数传给 消费者方的Stub ，然后把 Stub 暴露给用户，Stub 可以决定要不要去调Proxy。
 * 会通过代理类去完成这个调用，这样在Stub类中，就可以做一些额外的事，来对服务的调用过程进行优化或者容错的处理。附图：
 */

/**
 * 实现 ProxyFactory 接口，Stub 代理工厂 Wrapper 实现类，基于 Dubbo SPI Wrapper 机制加载
 *
 * <dubbo:reference interface="com.alibaba.dubbo.demo.DemoService" stub="com.alibaba.dubbo.demo.consumer.DemoServiceStub">
 *
 * 注意：服务实现的 Service ，不支持 Stub 存根。所以，虽然 <dubbo:service /> 有 stub 配置项，但是实际是没有效果的
 */
public class StubProxyFactoryWrapper implements ProxyFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubProxyFactoryWrapper.class);
    /**
     * ProxyFactory$Adaptive 对象
     */
    private final ProxyFactory proxyFactory;
    /**
     * Protocol$Adaptive 对象
     */
    private Protocol protocol;

    public StubProxyFactoryWrapper(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        T proxy = proxyFactory.getProxy(invoker);// 获得 Service Proxy 对象
        if (GenericService.class != invoker.getInterface()) {// 非泛化引用（若是泛化引用，不支持使用本地存根）
            // 获得 `stub` 配置项
            String stub = invoker.getUrl().getParameter(Constants.STUB_KEY, invoker.getUrl().getParameter(Constants.LOCAL_KEY));
            if (ConfigUtils.isNotEmpty(stub)) {
                Class<?> serviceType = invoker.getInterface();
                if (ConfigUtils.isDefault(stub)) {// `stub = true` 的情况，使用接口 + `Stub` 字符串。
                    if (invoker.getUrl().hasParameter(Constants.STUB_KEY)) {
                        stub = serviceType.getName() + "Stub";
                    } else {
                        stub = serviceType.getName() + "Local";
                    }
                }
                try {
                    Class<?> stubClass = ReflectUtils.forName(stub);// 加载 Stub 类
                    if (!serviceType.isAssignableFrom(stubClass)) {
                        throw new IllegalStateException("The stub implementation class " + stubClass.getName() + " not implement interface " + serviceType.getName());
                    }
                    try {
                        // 创建 Stub 对象，使用带 Service Proxy 对象的构造方法
                        Constructor<?> constructor = ReflectUtils.findConstructor(stubClass, serviceType);
                        proxy = (T) constructor.newInstance(new Object[]{proxy});// 核心逻辑
                        //export stub service
                        URL url = invoker.getUrl();
                        if (url.getParameter(Constants.STUB_EVENT_KEY, Constants.DEFAULT_STUB_EVENT)) {
                            url = url.addParameter(Constants.STUB_EVENT_METHODS_KEY, StringUtils.join(Wrapper.getWrapper(proxy.getClass()).getDeclaredMethodNames(), ","));
                            url = url.addParameter(Constants.IS_SERVER_KEY, Boolean.FALSE.toString());
                            try {
                                export(proxy, (Class) invoker.getInterface(), url);
                            } catch (Exception e) {
                                LOGGER.error("export a stub service error.", e);
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException("No such constructor \"public " + stubClass.getSimpleName() + "(" + serviceType.getName() + ")\" in stub implementation class " + stubClass.getName(), e);
                    }
                } catch (Throwable t) {
                    LOGGER.error("Failed to create stub implementation class " + stub + " in consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", cause: " + t.getMessage(), t);
                    // ignore
                }
            }
        }
        return proxy;
    }

    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException {
        return proxyFactory.getInvoker(proxy, type, url);
    }

    private <T> Exporter<T> export(T instance, Class<T> type, URL url) {
        return protocol.export(proxyFactory.getInvoker(instance, type, url));
    }

}