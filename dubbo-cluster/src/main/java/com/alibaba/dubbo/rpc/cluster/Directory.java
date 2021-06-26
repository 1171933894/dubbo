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
package com.alibaba.dubbo.rpc.cluster;

import com.alibaba.dubbo.common.Node;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Directory. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Directory_service">Directory Service</a>
 *
 * @see com.alibaba.dubbo.rpc.cluster.Cluster#join(Directory)
 */

/**
 * 定义了两个接口方法，分别返回服务的类型和 Invoker 集合。一个 Directory 只对应一个服务类型。
 * 中文直译为目录，代表了多个 Invoker ，可以把它看成 List<Invoker> 。但与 List 不同的是，
 * 它的值可能是动态变化的，比如注册中心推送变更
 *
 * 【重要子类】
 * 在Dubbo中，接口Directory的实现有RegistryDirectory和StaticDirectory两种，其中前者
 * 管理的invoker列表是根据服务注册中心的推送变化而变化的，而后者是当消费端使用了多注册中心时，
 * 把所有服务注册中心的invoker列表汇集到一个invoker列表中
 */
public interface Directory<T> extends Node {

    /**
     * get service type.
     *
     * @return service type.
     */
    /**
     * get service type.
     *
     * 获得服务类型，例如：com.alibaba.dubbo.demo.DemoService
     *
     * @return service type.
     */
    Class<T> getInterface();

    /**
     * list invokers.
     *
     * @return invokers
     */
    /**
     * list invokers.
     *
     * 获得所有服务 Invoker 集合
     *
     * @return invokers
     */
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

}