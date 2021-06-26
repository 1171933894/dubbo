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
package com.alibaba.dubbo.rpc.service;

/**
 * Generic service interface
 *
 * @export
 */
/**
 * 在 Spring 配置申明 generic="true"：
 * <dubbo:reference id="demoService"  interface="com.alibaba.dubbo.demo.DemoService" generic="true" />
 *
 * interface 配置项，泛化引用的服务接口。通过该配置，可以从注册中心，获取到所有该服务的提供方的地址。
 * generic 配置项，默认为 false ，不使用配置项。目前有三种配置项的值，开启泛化引用的功能：
 *      generic=true ，使用 com.alibaba.dubbo.common.utils.PojoUtils ，实现 POJO <=> Map 的互转。
 *      generic=nativejava ，使用 com.alibaba.dubbo.common.serialize.support.nativejava.NativeJavaSerialization ，实现 POJO <=> byte[] 的互转。
 *      generic=bean ，使用 com.alibaba.dubbo.common.beanutil.JavaBeanSerializeUtil ，实现 POJO <=> JavaBeanDescriptor 的互转。
 * 总的来说，三种方式的差异，在于使用互转( 序列化和反序列化 )的方式不同。未来如果我们有需要，完成可以实现 generic=json ，使用 FastJSON 来序列化和反序列化。
 */

/**
 * 注意：一个泛化引用，只对应一个服务实现
 */
public interface GenericService {

    /**
     * Generic invocation
     *
     * @param method         Method name, e.g. findPerson. If there are overridden methods, parameter info is
     *                       required, e.g. findPerson(java.lang.String)
     * @param parameterTypes Parameter types
     * @param args           Arguments
     * @return invocation return value
     * @throws Throwable potential exception thrown from the invocation
     */
    /**
     * Generic invocation
     * <p>
     * 泛化调用
     *
     * @param method         Method name, e.g. findPerson. If there are overridden methods, parameter info is
     *                       required, e.g. findPerson(java.lang.String)
     *                       方法名
     * @param parameterTypes Parameter types
     *                       参数类型数组
     * @param args           Arguments
     *                       参数数组
     * @return invocation return value 调用结果
     * @throws Throwable potential exception thrown from the invocation
     */
    Object $invoke(String method, String[] parameterTypes, Object[] args) throws GenericException;

}