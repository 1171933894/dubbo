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
package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;

import java.util.HashMap;
import java.util.Map;

/**
 * ContextInvokerFilter
 */

/**
 * 实现原理：
 * 在AbstractClusterInvoker类的invoke()方法内，把附加属性键值对放入RpcInvocation的attachments变量中，
 * 然后经过网络传输到服务提供端；服务提供端则使用ContextFilter对请求进行拦截，并从RpcInvocation中获取attachments
 * 中的键值对，设值到RpcContext中。
 *
 * 【注意】用于框架继承，不建议常规业务使用
 */
@Activate(group = Constants.PROVIDER, order = -10000)
public class ContextFilter implements Filter {

    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 创建新的 `attachments` 集合，清理公用的隐式参数
        Map<String, String> attachments = invocation.getAttachments();
        // 在此过滤器(例如rest协议)之前，我们可能已经在RpcContext中添加了一些附件。
        if (attachments != null) {
            attachments = new HashMap<String, String>(attachments);
            attachments.remove(Constants.PATH_KEY);
            attachments.remove(Constants.GROUP_KEY);
            attachments.remove(Constants.VERSION_KEY);
            attachments.remove(Constants.DUBBO_VERSION_KEY);
            attachments.remove(Constants.TOKEN_KEY);
            attachments.remove(Constants.TIMEOUT_KEY);
            attachments.remove(Constants.ASYNC_KEY);// Remove async property to avoid being passed to the following invoke chain.// 清空消费端的异步参数
        }
        // 设置 RpcContext 对象
        RpcContext.getContext()
                .setInvoker(invoker)
                .setInvocation(invocation)
                .setAttachments(attachments)
                .setLocalAddress(invoker.getUrl().getHost(),
                        invoker.getUrl().getPort());
        // 设置 RpcInvocation 对象的 `invoker` 属性
        if (invocation instanceof RpcInvocation) {
            ((RpcInvocation) invocation).setInvoker(invoker);
        }
        try {
            // 服务调用
            return invoker.invoke(invocation);
        } finally {
            // 移除上下文
            RpcContext.removeContext();
        }
    }
}