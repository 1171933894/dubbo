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
package com.alibaba.dubbo.rpc.cluster.support;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Directory;
import com.alibaba.dubbo.rpc.cluster.LoadBalance;
import com.alibaba.dubbo.rpc.support.RpcUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AbstractClusterInvoker
 *
 * Invoker 类的代理模式变形
 */
public abstract class AbstractClusterInvoker<T> implements Invoker<T> {

    private static final Logger logger = LoggerFactory
            .getLogger(AbstractClusterInvoker.class);
    /**
     * Directory 对象
     */
    protected final Directory<T> directory;// Directory 对象。通过它，可以获得所有服务提供者的 Invoker 对象
    /**
     * 集群时是否排除非可用( available )的 Invoker ，默认为 true
     */
    protected final boolean availablecheck;

    private AtomicBoolean destroyed = new AtomicBoolean(false);
    /**
     * 粘滞连接 Invoker
     *
     * http://dubbo.apache.org/zh-cn/docs/user/demos/stickiness.html
     * 粘滞连接用于有状态服务，尽可能让客户端总是向同一提供者发起调用，除非该提供者挂了，再连另一台。
     * 粘滞连接将自动开启延迟连接，以减少长连接数。
     */
    private volatile Invoker<T> stickyInvoker = null;

    public AbstractClusterInvoker(Directory<T> directory) {
        this(directory, directory.getUrl());
    }

    public AbstractClusterInvoker(Directory<T> directory, URL url) {
        // 初始化 directory
        if (directory == null)
            throw new IllegalArgumentException("service directory == null");

        this.directory = directory;
        //sticky: invoker.isAvailable() should always be checked before using when availablecheck is true.
        // 初始化 availablecheck
        this.availablecheck = url.getParameter(Constants.CLUSTER_AVAILABLE_CHECK_KEY, Constants.DEFAULT_CLUSTER_AVAILABLE_CHECK);
    }

    public Class<T> getInterface() {
        return directory.getInterface();
    }

    public URL getUrl() {
        return directory.getUrl();
    }

    public boolean isAvailable() {
        // 如有粘滞连接 Invoker ，基于它判断
        Invoker<T> invoker = stickyInvoker;// 指向，避免并发
        if (invoker != null) {
            return invoker.isAvailable();
        }
        return directory.isAvailable();// 基于 Directory 判断
    }

    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            directory.destroy();
        }
    }

    /**
     * Select a invoker using loadbalance policy.</br>
     * a)Firstly, select an invoker using loadbalance. If this invoker is in previously selected list, or, if this invoker is unavailable, then continue step b (reselect), otherwise return the first selected invoker</br>
     * b)Reslection, the validation rule for reselection: selected > available. This rule guarantees that the selected invoker has the minimum chance to be one in the previously selected list, and also guarantees this invoker is available.
     *
     * @param loadbalance load balance policy
     * @param invocation
     * @param invokers invoker candidates
     * @param selected  exclude selected invokers or not
     * @return
     * @throws RpcExceptione
     */
    /**
     * 从候选的 Invoker 集合，选择一个最终调用的 Invoker 对象
     *
     * @param loadbalance Loadbalance 对象，提供负责均衡策略
     * @param invocation  Invocation 对象
     * @param invokers    候选的 Invoker 集合
     * @param selected    已选过的 Invoker 集合. 注意：输入保证不重复
     * @return 最终的 Invoker 对象
     * @throws RpcException 当发生 RpcException 时
     */
    protected Invoker<T> select(LoadBalance loadbalance, Invocation invocation, List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {
        if (invokers == null || invokers.size() == 0)
            return null;
        String methodName = invocation == null ? "" : invocation.getMethodName();

        // 获得 sticky 配置项，方法级
        boolean sticky = invokers.get(0).getUrl().getMethodParameter(methodName, Constants.CLUSTER_STICKY_KEY, Constants.DEFAULT_CLUSTER_STICKY);
        {
            //ignore overloaded method
            // 若 stickyInvoker 不存在于 invokers 中，说明不在候选中，需要置空，重新选择
            if (stickyInvoker != null && !invokers.contains(stickyInvoker)) {
                stickyInvoker = null;
            }
            //ignore cucurrent problem
            // 若开启粘滞连接的特性，且 stickyInvoker 不存在于 selected 中，则返回 stickyInvoker 这个 Invoker 对象
            if (sticky && stickyInvoker != null && (selected == null || !selected.contains(stickyInvoker))) {
                // 若开启排除非可用的 Invoker 的特性，则校验 stickyInvoker 是否可用。若可用，则进行返回
                if (availablecheck && stickyInvoker.isAvailable()) {
                    return stickyInvoker;
                }
            }
        }
        // 执行选择
        Invoker<T> invoker = doselect(loadbalance, invocation, invokers, selected);

        // 若开启粘滞连接的特性，记录最终选择的 Invoker 到 stickyInvoker
        if (sticky) {
            stickyInvoker = invoker;
        }
        return invoker;
    }

    // 从候选的 Invoker 集合，选择一个最终调用的 Invoker 对象。代码如下
    private Invoker<T> doselect(LoadBalance loadbalance, Invocation invocation, List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {
        if (invokers == null || invokers.size() == 0)
            return null;
        if (invokers.size() == 1)
            return invokers.get(0);// 【第一种】如果只有一个 Invoker ，直接选择
        // If we only have two invokers, use round-robin instead.
        // 【第二种】如果只有两个 Invoker ，退化成轮循
        if (invokers.size() == 2 && selected != null && selected.size() > 0) {
            /**
             * 这里退化成轮询的实现有问题，对应源码return selected.get(0) == invokers.get(0) ? invokers.get(1) : invokers.get(0)；如果retries=4，即最多调用5次，且两个可选invoke分别为：
             *
             * 10.0.0.1:20884，10.0.0.1:20886；
             *
             * 那么5次选择的invoke为：
             *
             * 10.0.0.1:20884
             * 10.0.0.1:20886
             * 10.0.0.1:20886
             * 10.0.0.1:20886
             * 10.0.0.1:20886，
             * 即除了第1次外后面的选择都是选择第二个invoker;
             *
             * 因次需要把selected.get(0)修改为：selected.get(selected.size()-1)；
             *
             * 即每次拿前一次选择的invoker与 invokers.get(0)比较，如果相同，则选则另一个invoker；否则就选 invokers.get(0)；
             */
            return selected.get(0) == invokers.get(0) ? invokers.get(1) : invokers.get(0);
        }
        // 【第三种】使用 Loadbalance ，选择一个 Invoker 对象。
        Invoker<T> invoker = loadbalance.select(invokers, getUrl(), invocation);

        //If the `invoker` is in the  `selected` or invoker is unavailable && availablecheck is true, reselect.
        // 如果 selected中包含（优先判断） 或者 不可用&&availablecheck=true 则重试.
        if ((selected != null && selected.contains(invoker))
                || (!invoker.isAvailable() && getUrl() != null && availablecheck)) {
            try {
                //【第四种】重选一个 Invoker 对象
                Invoker<T> rinvoker = reselect(loadbalance, invocation, invokers, selected, availablecheck);
                if (rinvoker != null) {
                    invoker = rinvoker;
                } else {
                    //Check the index of current selected invoker, if it's not the last one, choose the one at index+1.
                    // 【第五种】看下第一次选的位置，如果不是最后，选+1位置.
                    int index = invokers.indexOf(invoker);
                    try {
                        //Avoid collision
                        // 最后在避免碰撞
                        invoker = index < invokers.size() - 1 ? invokers.get(index + 1) : invoker;
                    } catch (Exception e) {
                        logger.warn(e.getMessage() + " may because invokers list dynamic change, ignore.", e);
                    }
                }
            } catch (Throwable t) {
                logger.error("clustor relselect fail reason is :" + t.getMessage() + " if can not slove ,you can set cluster.availablecheck=false in url", t);
            }
        }
        return invoker;
    }

    /**
     * Reselect, use invokers not in `selected` first, if all invokers are in `selected`, just pick an available one using loadbalance policy.
     *
     * @param loadbalance
     * @param invocation
     * @param invokers
     * @param selected
     * @return
     * @throws RpcException
     */
    private Invoker<T> reselect(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected, boolean availablecheck)
            throws RpcException {

        //Allocating one in advance, this list is certain to be used.
        // 预先分配一个，这个列表是一定会用到的.
        List<Invoker<T>> reselectInvokers = new ArrayList<Invoker<T>>(invokers.size() > 1 ? (invokers.size() - 1) : invokers.size());

        //First, try picking a invoker not in `selected`.
        // 先从非select中选
        if (availablecheck) { // invoker.isAvailable() should be checked
            // 获得非选择过，并且可用的 Invoker 集合
            for (Invoker<T> invoker : invokers) {
                if (invoker.isAvailable()) {
                    if (selected == null || !selected.contains(invoker)) {
                        reselectInvokers.add(invoker);
                    }
                }
            }
            // 使用 Loadbalance ，选择一个 Invoker 对象
            if (reselectInvokers.size() > 0) {
                return loadbalance.select(reselectInvokers, getUrl(), invocation);
            }
        } else { // do not check invoker.isAvailable()
            // 获得非选择过的 Invoker 集合
            for (Invoker<T> invoker : invokers) {
                if (selected == null || !selected.contains(invoker)) {
                    reselectInvokers.add(invoker);
                }
            }
            // 使用 Loadbalance ，选择一个 Invoker 对象
            if (reselectInvokers.size() > 0) {
                return loadbalance.select(reselectInvokers, getUrl(), invocation);
            }
        }
        // Just pick an available invoker using loadbalance policy
        {
            // 获得选择过的，并且可用的 Invoker 集合
            if (selected != null) {
                for (Invoker<T> invoker : selected) {
                    if ((invoker.isAvailable()) // available first
                            && !reselectInvokers.contains(invoker)) {
                        reselectInvokers.add(invoker);
                    }
                }
            }
            // 使用 Loadbalance ，选择一个 Invoker 对象
            if (reselectInvokers.size() > 0) {
                return loadbalance.select(reselectInvokers, getUrl(), invocation);
            }
        }
        return null;
    }

    public Result invoke(final Invocation invocation) throws RpcException {

        checkWhetherDestroyed();// 校验是否销毁

        LoadBalance loadbalance;// 获得 LoadBalance 对象

        List<Invoker<T>> invokers = list(invocation);// 获得所有服务提供者 Invoker 集合
        if (invokers != null && invokers.size() > 0) {
            loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                    .getMethodParameter(invocation.getMethodName(), Constants.LOADBALANCE_KEY, Constants.DEFAULT_LOADBALANCE));
        } else {
            loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(Constants.DEFAULT_LOADBALANCE);
        }
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        return doInvoke(invocation, invokers, loadbalance);// 执行调用
    }

    protected void checkWhetherDestroyed() {

        if (destroyed.get()) {
            throw new RpcException("Rpc cluster invoker for " + getInterface() + " on consumer " + NetUtils.getLocalHost()
                    + " use dubbo version " + Version.getVersion()
                    + " is now destroyed! Can not invoke any more.");
        }
    }

    @Override
    public String toString() {
        return getInterface() + " -> " + getUrl().toString();
    }

    protected void checkInvokers(List<Invoker<T>> invokers, Invocation invocation) {
        if (invokers == null || invokers.size() == 0) {
            throw new RpcException("Failed to invoke the method "
                    + invocation.getMethodName() + " in the service " + getInterface().getName()
                    + ". No provider available for the service " + directory.getUrl().getServiceKey()
                    + " from registry " + directory.getUrl().getAddress()
                    + " on the consumer " + NetUtils.getLocalHost()
                    + " using the dubbo version " + Version.getVersion()
                    + ". Please check if the providers have been started and registered.");
        }
    }

    protected abstract Result doInvoke(Invocation invocation, List<Invoker<T>> invokers,
                                       LoadBalance loadbalance) throws RpcException;

    /**
     * 获得所有服务提供者 Invoker 集合
     */
    protected List<Invoker<T>> list(Invocation invocation) throws RpcException {
        List<Invoker<T>> invokers = directory.list(invocation);
        return invokers;
    }
}
