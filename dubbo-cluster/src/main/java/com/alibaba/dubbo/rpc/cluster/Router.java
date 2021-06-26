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

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Router. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Routing">Routing</a>
 *
 * @see com.alibaba.dubbo.rpc.cluster.Cluster#join(Directory)
 * @see com.alibaba.dubbo.rpc.cluster.Directory#list(Invocation)
 */

/**
 * 实现 Comparable 接口，路由规则接口
 *
 * 【作用】
 * 路由接口会根据用户配置的不同路由策略对Invoker列表进行过滤，只
 * 返回符合规则的Invoker。例如：如果用户配置了接口A的所有调用，都
 * 使用IP为192.168.1.22的节点，则路由会过滤其他的Invoker，只
 * 返回IP为192.168.1.22的Invoker。
 */
public interface Router extends Comparable<Router> {

    /**
     * get the router url.
     *
     * @return url
     */
    /**
     * 路由规则 URL
     */
    URL getUrl();

    /**
     * route.
     *
     * @param invokers
     * @param url        refer url
     * @param invocation
     * @return routed invokers
     * @throws RpcException
     */
    /**
     * route.
     *
     * 路由，筛选匹配的 Invoker 集合
     *
     * @param invokers   Invoker 集合
     * @param url        refer url
     * @param invocation
     * @return routed invokers 路由后的 Invoker 集合
     * @throws RpcException
     */
    <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException;

}